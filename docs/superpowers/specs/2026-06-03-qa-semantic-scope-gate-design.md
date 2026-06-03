# 课程问答语义相关性闸门设计稿

> 日期：2026-06-03
> 适用范围：`backend/ckqa-back/`（`qa/routing/`、`course/routing/`、`config/`、对应测试）
> 目标：把课程问答的"无关问题拦截"从脆弱的关键词黑名单，替换为复用课程画像 embedding 的单课程语义相关性判定；保守偏向，宁可漏过也不误拦真问题。

## 1. 背景与问题

学生端提问时，前端会在发送前调用 `/api/v1/qa-routing/domain-check`，由后端 `QaQuestionDomainGuardService` 判断问题是否属于课程资料问答范围；`out_of_scope` 则前端阻断并提示。

当前判定是**完全一致的中文子串关键词匹配**（`isCampusLife` / `isCreativeWriting` / `isProfileHelp` / `isCourseAdministrivia`）。它的根本缺陷在一次真实问题中暴露：

- 用户输入 `今天晚上吃什哦`（`今天晚上吃什么` 的错别字，`么`→`哦`）。
- 词典里有 `吃什么`，但输入是 `吃什哦`，`String.contains("吃什么")` 为 false；其余 dining 词也错开（词典是 `晚饭吃`，输入是 `晚上吃`，且无 `食堂`）。
- `classify()` 返回 `allowed()`，问题进入检索模式。

更深层的结构问题：

1. **默认放行 + 关键词黑名单**：只要不命中黑名单一律放行，错别字、同义词、未登录表达天然失效。
2. **模式路由器无"无关"概念**：`QaModeRoutingService` 必然返回 `basic/local/global/drift/hybrid_v0` 之一，守卫一旦漏判，后段无条件进入检索模式。
3. **在线路径无语义判定**：无关检测只有这一处关键词匹配；`QaWorkflowService` 主流程不调用守卫，也没有相关性闸门。

同时，项目已经具备语义判定所需的全部基建（用于自动选课路由）：

- `CourseProfileTextBuilder` 构造稳定课程画像文本（课程名/简介/目标 + 知识库名 + 章节标题≤48 + 画像关键词≤128 + 资料名）。
- `CourseRoutingService.ensureProfiles()` 保证画像已 embedding 并写入 LanceDB。
- `graphRagClient.recommend(question, courseIds, limit)` 返回每个候选课程的 `confidence`。
- Python 侧 `confidence` 是**生的余弦相似度**（`InMemory` 为 `_cosine_similarity`；`LanceDB` 为 `1 - cosine_distance`，clamp 到 `[0,1]`），**不跨候选归一化**。因此只传一门课也能得到有意义的绝对相似度。

## 2. 设计目标

1. **替换判定内核**：`QaQuestionDomainGuardService` 的关键词逻辑替换为"与已确定课程画像的语义相关性"判定。
2. **复用单一出处**：embedding / 画像逻辑复用 `course.routing`，不重复实现。
3. **保守偏向**：误拦真问题的代价远大于偶尔放过无关问题；多条放行优先规则确保安全。
4. **fail-open**：embedding 服务故障、画像缺失、未接线、未启用，一律放行。
5. **前端零改动**：`/domain-check` 请求/响应契约不变（前端只读 `status`/`message`）。
6. **可校准**：闸门判定打结构化日志，便于上线后用真实数据校准阈值。

本轮不做：

1. 不为闸门新建数据库表做系统化校准评估（先用日志，YAGNI）。
2. 不改 `QaModeRoutingService` 的模式判定逻辑。
3. 不把无关判定下沉到 `QaWorkflowService` 后端提交链（仍由前端发送前的 `/domain-check` 闸门承担）。
4. 不保留任何关键词兜底（这是方案 B 的范畴；本设计为纯语义的方案 A）。
5. 不复用高层 `CourseRoutingService.recommend()`（它把故障压成 `no_match`，与 fail-open 冲突，且会污染 `course_route_decisions` 语义）。

## 3. 架构与数据流

闸门位置与调用时机完全不变，只替换内部判定。关键事实：前端在调 `/domain-check` **之前**已经 `resolveCourse` 成功（否则提前 return），所以闸门**永远只针对一门已确定的课程**判断。

```text
send()
  -> resolveCourse(text)                       // 已确定唯一课程
  -> checkQuestionDomainBeforeSend             // POST /domain-check {courseId, question, hasConversationContext}
       -> QaRoutingController.checkDomain
       -> QaQuestionDomainGuardService.check()
            ├─ 鉴权 + 解析有效 courseId（沿用现有 resolveScope）
            └─ classify(courseId, question, hasContext):
                 ① hasConversationContext        -> allowed（追问放行，不校验）
                 ② provider 未接线 / 未启用       -> allowed（fail-open）
                 ③ evaluateScopeRelevance(courseId, question):
                      ensureProfiles(该课) -> graphRagClient.recommend([courseId], 1) -> 余弦相似度
                 ④ evaluated 且 confidence < 阈值  -> out_of_scope
                    否则                          -> allowed
  -> 前端：out_of_scope 阻断并提示；否则进入模式路由
```

判定粒度是**课程级**（画像按课程构建），与已确定的那门课比较，符合"单课程绝对相似度"语义。

## 4. 接口与组件

### 4.1 新增窄接口（course.routing，保持边界清晰）

```java
public interface CourseScopeRelevanceProvider {
    ScopeRelevance evaluateScopeRelevance(String courseId, String question);
}

public record ScopeRelevance(boolean evaluated, double confidence) {
    public static ScopeRelevance notEvaluated() { return new ScopeRelevance(false, 0d); }
    public static ScopeRelevance evaluated(double c) { return new ScopeRelevance(true, c); }
}
```

`evaluated=false` 表示"没能算出有效相似度"，调用方据此 fail-open。

### 4.2 `CourseRoutingService` 实现（复用私有 `ensureProfiles` + 低层 client）

```java
public ScopeRelevance evaluateScopeRelevance(String courseId, String question) {
    if (!properties.isEnabled() || !hasText(courseId) || !hasText(question)) {
        return ScopeRelevance.notEvaluated();
    }
    try {
        Courses course = coursesService.getOne(
                new LambdaQueryWrapper<Courses>().eq(Courses::getCourseId, courseId));
        if (course == null || !isRoutableCourse(course)) {
            return ScopeRelevance.notEvaluated();                 // fail-open
        }
        ensureProfiles(List.of(course));
        var resp = graphRagClient.recommend(
                new GraphRagCourseRoutingRecommendRequest(question, List.of(courseId), 1));
        var cands = resp == null ? null : resp.candidates();
        if (cands == null || cands.isEmpty()) {
            return ScopeRelevance.notEvaluated();                 // fail-open
        }
        return ScopeRelevance.evaluated(cands.get(0).confidence());
    } catch (RuntimeException ex) {
        return ScopeRelevance.notEvaluated();                     // 故障一律 fail-open
    }
}
```

要点：**不写 `course_route_decisions`**（那是选课路由语义），闸门只读相似度；故障路径不复用高层 `recommend()` 的 `no_match`。

### 4.3 `QaQuestionDomainGuardService` 改造

- 删除全部关键词方法（`isCampusLife` / `isCreativeWriting` / `isProfileHelp` / `isCourseAdministrivia` / `containsAny` / `normalize`）。
- `resolveScope` 改为返回有效 `courseId`（优先级：会话课程 > 请求课程 > 知识库所属课程）。
- 用 `@Autowired(required=false)` setter 注入 `CourseScopeRelevanceProvider`（与现有 `courseAccessService` 同模式）→ 保留 2 参构造器、`provider` 为空即 fail-open。
- `classify` 改为：

```java
private QaQuestionDomainCheckResponse classify(String courseId, String question, boolean hasContext) {
    if (hasContext) return QaQuestionDomainCheckResponse.allowed();              // 追问放行
    if (relevanceProvider == null) return QaQuestionDomainCheckResponse.allowed(); // 未接线 fail-open
    ScopeRelevance r = relevanceProvider.evaluateScopeRelevance(courseId, question);
    if (r.evaluated() && r.confidence() < properties.getOutOfScopeThreshold()) {
        return QaQuestionDomainCheckResponse.outOfScope("low_course_relevance", OUT_OF_SCOPE_MESSAGE);
    }
    return QaQuestionDomainCheckResponse.allowed();
}
```

## 5. 阈值与配置

新增 `QaDomainGuardProperties`（前缀 `ckqa.qa-domain-guard`）：

| 配置项 | 默认 | 含义 |
|---|---|---|
| `enabled` | `true` | 总开关；关掉=全放行 |
| `out-of-scope-threshold` | `0.25`（2026-06-03 实测校准） | 余弦相似度低于它才判无关 |

阈值说明：`0.25` 由 §8.1 校准工具在真实环境实测后回填——取 in_scope 下沿（≈0.31）与 out_of_scope 上沿（≈0.19）经验间隙的中点。中文 embedding 对无关文本的余弦相似度基线偏高，但对**明显**无关文本仍显著低于切题文本，间隙清晰。

- 默认值取经验间隙中点、偏向保守，宁可漏过也不误拦。
- 闸门在 `QaQuestionDomainGuardService.classify` 中每次判定打一条结构化日志：question 的 hash、courseId、confidence、阈值、decision。
- 暂不为闸门新建数据库表；以后要做系统化校准评估再加。

## 6. 分支逻辑（保守偏向的体现）

四条放行优先规则，确保"误拦真问题"概率最低：

1. **追问放行**：`hasConversationContext=true` → 直接 `allowed`（会话已在课程语境里）。
2. **未接线/未启用放行**：`provider == null` 或 `enabled=false` → `allowed`。
3. **故障放行**：embedding 服务异常、画像缺失、候选为空、课程不可路由 → `notEvaluated` → `allowed`。
4. **仅明显跑题才拦**：只有"确实算出相似度 **且** 低于阈值"才 `out_of_scope`。

学生提示文案（避免"相似度/embedding"等术语）：

> 这个问题好像不在当前课程的资料范围内，换成课程里的概念、章节或知识点再试试吧。

`reasonCode` 改为 `low_course_relevance`，`strategy` 改为 `semantic_relevance_v1`（前端只读 `status`/`message`，不受影响）。

## 7. 改动文件清单

后端（改动集中在 2 个包）：

- 🆕 `course/routing/CourseScopeRelevanceProvider.java`（接口 + `ScopeRelevance` record）
- ✏️ `course/routing/CourseRoutingService.java`（实现 `evaluateScopeRelevance`，复用 `ensureProfiles`、`isRoutableCourse`）
- ✏️ `qa/routing/QaQuestionDomainGuardService.java`（删关键词、注入 provider、改 `classify`、`resolveScope` 返回 courseId、打日志）
- 🆕 `config/QaDomainGuardProperties.java` + 在配置类上 `@EnableConfigurationProperties`
- ✏️ `qa/dto/QaQuestionDomainCheckResponse.java`（`strategy`/`reasonCode` 文案；新增带 message 的 `outOfScope` 已具备）

测试：

- ✏️ 重写 `QaQuestionDomainGuardServiceTest`（mock provider：低分→`out_of_scope`；追问→`allowed`；`provider==null`→`allowed`；`notEvaluated`→`allowed`；保留鉴权/scope 用例）
- 🆕 `CourseRoutingService.evaluateScopeRelevance` 单测（mock `graphRagClient`：正常返回置信度；抛异常→`notEvaluated`；候选为空→`notEvaluated`）

前端：

- ✅ 不改（`/domain-check` 契约不变）。

## 8. 测试与校准策略（TDD）

- 先写失败测试再实现：`evaluateScopeRelevance` 的故障→fail-open、`classify` 的四条放行 + 低分拦截。
- 旧的关键词断言（`今天晚上吃什么 → campus_life` 等 reasonCode）整体移除，替换为语义 mock 断言。
- 阈值校准：上线后既可从 §5 的结构化日志收集 (confidence, 人工标注是否真无关)，也可用 §8.1 的离线校准工具在真实 embedding 环境批量实测两类问题分布后回填阈值。

### 8.1 阈值校准工具

闸门用单一全局余弦阈值；下列工具用于在真实环境实测两类问题的相似度分布、算出可回填的阈值，并随新课程画像复用。

- **纯逻辑核心** `qa/routing/QaScopeGateCalibrationReport`（test-scope，无外部依赖、可单测）：输入每条样本的 `(courseId, label, evaluated, confidence)`，输出按课程的 in_scope / out_of_scope 相似度分布（min/中位/max），并在「in_scope 零误拦」为硬约束下扫描 `[0.15, 0.45]`（步长 `0.01`）找能拦下最多无关问题的**单一全局阈值**；`evaluated=false`（fail-open）不计入、单独计数。已有 `QaScopeGateCalibrationReportTest` 覆盖。
- **可复用验证集** `src/test/resources/qa-scope/qa-scope-validation-v1.jsonl`，每行 `{"id","courseId","question","label"}`（`label` ∈ `in_scope` / `out_of_scope`）：
  - `courseId="*"` 是**共享无关问题池**，对验证集里出现的每一门 in_scope 课程各评测一遍；
  - **扩展新课程**：只需为新课追加若干 `in_scope` 行（`courseId` 写新课 id），共享无关池自动套用到新课——不改任何代码、跑同一条命令即可。
- **在线校准壳** `qa/routing/QaScopeGateOnlineCalibrationTest`（`@SpringBootTest`，默认禁用，需 `-Dckqa.qaScopeGate.onlineCalibration=true` 才跑，复用课程路由校准同一套 MySQL/GraphRAG/embedding 接线）：逐条调 `evaluateScopeRelevance` 取真实相似度喂给纯逻辑核心，打印两类分布 + 推荐阈值，再把推荐值回填到 `ckqa.qa-domain-guard.out-of-scope-threshold`。
- **关键取舍**：闸门维持**单一全局阈值**（不改 gate 配置模型）；工具按课打分布只为暴露离群课程。若将来确需按课设阈值，那是改 gate 本身、另开一轮。

**首次校准结果（2026-06-03，课程 `crs-20260506-r4slkr` / text-embedding-v4）**：12 条切题相似度落在 `[0.306, 0.505]`（中位 0.424），8 条无关里 7 条明显无关落在 `[0.060, 0.188]`，经验间隙 `(0.188, 0.306)`。取中点回填 **`0.25`**（零误拦、拦下 7/8 无关，双向余量均衡）。唯一漏网为"这门课什么时候期末考试？"（0.480）——它语义贴近课程本身、高于多数切题，纯语义阈值无法在不误伤真问题的前提下拦它，属意图分类范畴，本设计不处理。

## 9. 风险与权衡

1. **延迟**：会话已绑定课程时选课路由不跑，本闸门会**新增一次到 Python 的 embedding 往返**（量级与选课路由的一次 embedding 相当）。可接受；后续可在选课路由已跑时复用其 embedding，本轮不做。
2. **阈值依赖模型分布**：绝对余弦阈值依赖 embedding 模型，已于 2026-06-03 用 text-embedding-v4 对 OS 课实测校准为 `0.25`（见 §8.1）；接入新课程画像后建议用同一工具复校。保守取间隙中点 + fail-open 把误拦风险降到最低。
3. **依赖方向**：`qa.routing` 新增对 `course.routing` 的依赖（经窄接口），与现有 `CourseAccessService` 依赖同向，可接受。
4. **画像未就绪的课程**：`ensureProfiles` 会按需 embedding；若 Python 不可用则走 fail-open，不阻断提问。
