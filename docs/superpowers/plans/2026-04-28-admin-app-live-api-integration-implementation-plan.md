# Admin App Live API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `frontend/apps/admin-app` 中 `/app/health`、`/app/courses`、课程详情、资料生命周期、知识库列表/详情、知识库构建向导逐步从 mock 数据切到 Java `backend/ckqa-back` 的 `/api/v1`，并保持 `DataSourceChip` 只在真实接入页面显示 `live`。

**Architecture:** 前端新增 `src/api/*` 作为 Java `ApiResponse` 解包与业务 API 边界，`ModulePage.vue` 从静态配置渲染升级为“配置 + loader + 页面/区块状态”的渲染器；后端补最小课程/知识库聚合读接口，解析、导出、索引、QA 操作继续复用已有工作流接口；长任务由共享 `long-task-state.js` 统一处理 HTTP 超时后的资源轮询兜底。

**Tech Stack:** Vue 3、Vite、Node `node --test`、Axios、Spring Boot 4.0.5、Java 21、MyBatis-Plus、Maven Wrapper、MySQL。

---

## 0. Source Of Truth

- 设计稿：`docs/superpowers/specs/2026-04-28-admin-app-live-api-integration-design.md`
- 设计稿提交：`f7a2af1 docs: add admin app live api integration design`
- 前端正式边界：只访问 Java `/api/v1`，不直接访问 GraphRAG Python `/v1`。
- 当前代码事实：`ApiPageData` 后端字段为 `current / size / total / pages`，前端 loader 统一归一为 `pagination: { page, size, total, pages }`，其中 `page = data.page ?? data.current ?? 1`。
- 当前代码事实：`backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/KnowledgeBaseSummaryResponse.java` 已存在，表示“课程下知识库摘要”；本计划新增的知识库列表 DTO 放在 `org.ysu.ckqaback.index.dto` 包，避免同包命名冲突。

## 1. Phase 0 - Implementation Guardrails

- [ ] 执行 `git status --short`，确认只存在本任务相关未提交改动。
- [ ] 阅读这些文件，确认实现基线没有漂移：
  - `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
  - `frontend/apps/admin-app/src/views/pages/module-content.js`
  - `frontend/apps/admin-app/src/components/common/DataTableShell.vue`
  - `frontend/apps/admin-app/src/axios/index.js`
  - `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`
  - `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java`
  - `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
  - `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- [ ] 不修改 `.env`、`node_modules/`、生成输出、GraphRAG Python `/v1` 调用路径。
- [ ] 每个阶段完成后运行对应测试，不把失败测试留到下一阶段。

## 2. Phase 1 - Health And Courses Live

### 2.1 Backend Course List And Detail APIs

- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseQueryRequest.java`。

```java
package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程列表查询参数。
 */
@Getter
@Setter
public class CourseQueryRequest {

    @Min(value = 1, message = "page必须大于0")
    private long page = 1;

    @Min(value = 1, message = "size必须大于0")
    @Max(value = 100, message = "size最大为100")
    private long size = 20;

    private String keyword;
    private String status;
}
```

- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseSummaryResponse.java`，字段包含 `id/courseId/courseName/description/status/accessPolicy/materialCount/parsedMaterialCount/failedMaterialCount/knowledgeBaseCount/activeKnowledgeBaseCount/latestIndexRunId/latestIndexRunStatus/updatedAt`。
- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseDetailResponse.java`，首版包含 Summary 全部字段，额外保留 `createdAt/accessPolicyDescription/memberCount` 三个可为空字段。
- [ ] 更新 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`：
  - 注入 `CoursesService`、`CourseMaterialsService`、`KnowledgeBasesService`、`IndexRunsService`。
  - 新增 `ApiPageData<CourseSummaryResponse> listCourses(CourseQueryRequest request)`。
  - 新增 `CourseDetailResponse getCourseDetail(String courseId)`。
  - 聚合首版使用 Service 层查询和内存统计；课程量增长后再迁移到 Mapper SQL。
  - 课程不存在时抛 `BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND)`，消息覆写为“课程不存在”。本计划不新增业务码，避免影响既有响应码枚举。
- [ ] 更新 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`：

```java
@GetMapping
public ApiResponse<ApiPageData<CourseSummaryResponse>> listCourses(@Validated CourseQueryRequest request) {
    return ApiResponseUtils.success(courseLookupService.listCourses(request));
}

@GetMapping("/{courseId}")
public ApiResponse<CourseDetailResponse> getCourse(@PathVariable String courseId) {
    return ApiResponseUtils.success(courseLookupService.getCourseDetail(courseId));
}
```

- [ ] 扩展 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`：
  - `GET /api/v1/courses?page=1&size=20` 返回 `$.data.items[0].courseId == "os"`。
  - `GET /api/v1/courses/os` 返回 `$.data.courseId == "os"`。
  - 保留已有 `GET /api/v1/courses/os/pdf-files` 覆盖。

### 2.2 Frontend API Client And Error Contract

- [ ] 新建 `frontend/apps/admin-app/src/api/client.js`。
- [ ] 在 `client.js` 中导出：
  - `unwrapApiResponse(response)`：只接受 `code/message/data` envelope，且仅 `code === 200` 返回 `data`。
  - `normalizePageData(data)`：兼容 `items/current/size/total/pages` 与设计稿中的 `items/page/size/total/pages`。
  - `createApiError(error)`：将 Axios 错误、业务错误、非标准响应统一成 `{ message, status, code, data, nonStandard, raw }`。
  - `isBusinessCode(error, code)`：用于 `4093` 到 `4097` 分支。

```js
export function normalizePageData(data = {}) {
  return {
    items: Array.isArray(data.items) ? data.items : [],
    pagination: {
      page: Number(data.page ?? data.current ?? 1),
      size: Number(data.size ?? 20),
      total: Number(data.total ?? 0),
      pages: Number(data.pages ?? 0),
    },
    raw: data,
  }
}
```

- [ ] 更新 `frontend/apps/admin-app/src/axios/index.js`：
  - 保留 `API_BASE_URL` 默认值 `http://127.0.0.1:8080/api/v1`。
  - 保留认证头注入。
  - 响应拦截器只做传输层错误收敛，不解业务 envelope；业务解包放到 `src/api/client.js`。
- [ ] 新建 `frontend/apps/admin-app/src/api/system.js`：

```js
import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function getSystemHealth() {
  return unwrapApiResponse(await http.get('/system/health'))
}
```

- [ ] 新建 `frontend/apps/admin-app/src/api/courses.js`，导出：
  - `listCourses(params)`
  - `getCourse(courseId)`
  - `listCourseMaterials(courseId)`
  - `listCourseKnowledgeBases(courseId)`

### 2.3 Backend CORS For Local Admin App

- [ ] 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/WebCorsConfig.java`。
- [ ] 只对白名单本地来源开放 `/api/v1/**`：
  - `http://127.0.0.1:*`
  - `http://localhost:*`
- [ ] 允许方法包含 `GET/POST/PUT/PATCH/DELETE/OPTIONS`。
- [ ] 允许请求头包含 `Authorization`、`Content-Type`。
- [ ] 生产来源不在代码中硬编码；生产部署通过运行时配置收紧。
- [ ] 增加轻量 MVC 测试或配置测试，覆盖本地 origin 允许、非白名单 origin 不返回允许跨域头。

### 2.4 Health Auto Load

- [ ] 更新 `frontend/apps/admin-app/src/views/system/HealthView.vue`：
  - 从 `src/api/system.js` 调用 `getSystemHealth()`。
  - `onMounted()` 自动刷新一次。
  - 成功刷新时更新 `refreshedAt`。
  - 请求失败时保留上一次成功结果，显示错误面板。
  - `DataSourceChip` 继续固定 `source="live"` 并传入 `refreshedAt`。
- [ ] 更新 `frontend/apps/admin-app/src/app-shell.test.js`：
  - 保留“健康响应同时保留 reachable 和 ready”测试。
  - 增加 `getSystemHealth` 使用 `unwrapApiResponse` 的轻量单元覆盖，避免回退到手写 `response.data?.data`。

### 2.5 Courses Table Live Loader

- [ ] 新建 `frontend/apps/admin-app/src/views/pages/module-loaders.js`。
- [ ] 在 `module-loaders.js` 中实现 `loadModulePage(route, query)`，首批支持 `courses`，未接入页面返回 `null`。

```js
export async function loadModulePage(route, query = {}) {
  if (route.name === 'courses') {
    const pageData = await listCourses({
      page: query.page ?? 1,
      size: query.size ?? 20,
      keyword: query.keyword ?? '',
      status: query.status ?? '',
    })

    const { items, pagination, raw } = normalizePageData(pageData)

    return {
      source: 'live',
      requestState: items.length ? 'success' : 'empty',
      refreshedAt: new Date().toISOString(),
      rows: items.map(mapCourseRow),
      pagination,
      facts: [],
      workflowSteps: [],
      blocks: {},
      raw,
    }
  }

  return null
}
```

- [ ] 更新 `frontend/apps/admin-app/src/views/pages/module-content.js`：
  - `courses.dataSource` 改为 `live`。
  - `courses.rows` 改为空数组。
  - `courses.columns` 改成 `['课程', '状态', '资料', '知识库', '最近索引', '更新时间']`。
  - “新建课程”保留但加 `disabled: true` 和 tooltip 文案“课程创建接口未开放，当前仅支持读取已有课程。”。
  - `knowledge-base-build.workflowSteps` 从 6 步改成 5 步：`material/parse/export/index/smoke`。
- [ ] 更新 `frontend/apps/admin-app/src/components/common/DataTableShell.vue`：
  - 新增 `pagination` prop。
  - 新增 `loading` prop。
  - 新增 `error` prop。
  - 新增 `onPageChange` emit。
  - 表头计数使用 `pagination.total` 优先，没有分页时使用当前 rows 数量。
- [ ] 更新 `frontend/apps/admin-app/src/views/pages/ModulePage.vue`：
  - 引入 `onMounted/watch/reactive`。
  - 页面进入和路由 query 变化时调用 `loadModulePage()`。
  - loader 返回 `null` 时继续使用 `module-content.js` 的 mock 配置。
  - loader 成功时使用 live rows/pagination/refreshedAt。
  - loader 失败时 `DataSourceChip` 仍显示 `live`，页面展示错误面板和重试按钮。
  - 主操作按钮支持 `disabled` 和 `title`。
- [ ] 更新 `frontend/apps/admin-app/src/app-shell.test.js`：
  - `/app/courses` 数据来源断言从 `mock` 改为 `live`。
  - 增加 `normalizePageData({ current: 2 })` 映射为 `pagination.page === 2`。
  - 构建向导步骤断言改为 5 步且无 `activate`。

### 2.6 Phase 1 Verification

- [ ] 执行前端验证：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

- [ ] 执行后端验证：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

- [ ] 手动接口检查：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/system/health
curl --noproxy '*' http://127.0.0.1:8080/api/v1/courses
```

- [ ] 浏览器检查：
  - `/app/health` 首屏自动请求。
  - `/app/courses` 不显示静态 mock rows。
  - `/app/courses` 的 `DataSourceChip` 为“实时数据”。

## 3. Phase 2 - Course Detail And Material Lifecycle

### 3.1 Course Detail Live Blocks

- [ ] 在 `frontend/apps/admin-app/src/views/pages/module-loaders.js` 增加 `course-detail` loader：
  - 并行请求 `getCourse(courseId)`、`listCourseMaterials(courseId)`、`listCourseKnowledgeBases(courseId)`。
  - `getCourse(courseId)` 失败时返回页面级 `error`。
  - materials 或 knowledgeBases 失败时只设置对应 `blocks.<key>.state = 'error'`。
- [ ] 更新 `ModulePage.vue` overview 分支：
  - 如果存在 `blocks.course`，顶部摘要渲染真实课程字段。
  - 如果存在 `blocks.materials`，渲染资料区块，行入口为 `/app/materials/:materialId`。
  - 如果存在 `blocks.knowledgeBases`，渲染知识库区块，行入口为 `/app/knowledge-bases/:kbId` 和 `/app/knowledge-bases/:kbId/build`。
  - 区块 error 显示局部重试按钮，不触发整页 error。
- [ ] 更新 `app-shell.test.js`：
  - 课程详情 loader 中 materials 失败不会让 `requestState` 变成页面级 `error`。
  - 主资源失败才进入页面级 `error`。

### 3.2 Materials API And Page

- [ ] 新建 `frontend/apps/admin-app/src/api/materials.js`，导出：
  - `getMaterial(id)`
  - `listParseResults(id)`
  - `startParse(id)`
  - `exportGraphRag(id, payload)`
- [ ] 在 `materials.js` 中实现 `hasCompleteGraphRagExport(results, { mode, withPageDocs })`：
  - 只按 `fileName` 判断。
  - `section` 模式需要 `graphrag_normalized_docs.json` 和 `graphrag_section_docs.json`。
  - `withPageDocs=true` 还需要 `graphrag_page_docs.json`。
  - `page` 模式需要 `graphrag_normalized_docs.json` 和 `graphrag_page_docs.json`。
- [ ] 在 `module-loaders.js` 增加 `material-detail` loader：
  - 请求 `GET /pdf-files/{id}` 和 `GET /pdf-files/{id}/results`。
  - 根据 `parseStatus` 推导按钮状态。
  - `parseStatus=pending|failed` 才允许解析。
  - `parseStatus=processing` 禁用解析并提示执行中。
  - `parseStatus=done` 才允许导出。
- [ ] 更新 `ModulePage.vue` material overview：
  - 触发解析按钮调用 `startParse(id)`。
  - 导出按钮默认 payload 为 `{ mode: 'section', withPageDocs: true, force: false }`。
  - `force=true` 只在用户选择覆盖旧导出且二次确认后发送。
  - 解析结果列表只读展示，不提供 JSON 编辑器。

### 3.3 Long Task Fallback

- [ ] 新建 `frontend/apps/admin-app/src/views/pages/long-task-state.js`。
- [ ] 导出 `LONG_TASK_LIMITS`：

```js
export const LONG_TASK_LIMITS = {
  parse: { intervalMs: 10_000, timeoutMs: 900_000 },
  export: { intervalMs: 5_000, timeoutMs: 300_000 },
  index: { intervalMs: 30_000, timeoutMs: 1_800_000 },
}
```

- [ ] 导出 `shouldStartFallback(error)`：
  - Axios timeout。
  - 网络中断。
  - HTTP `504`。
  - 业务码 `4094` 和 `4095`。
- [ ] 导出 `createLongTaskController(options)`：
  - options 包含 `trigger/poll/isSuccess/isFailed/onState/onSuccess/onFailure/limits`。
  - 内部持有 `AbortController` 和 timer。
  - 返回 `{ start, cancel }`。
  - `cancel()` 清理 timer 并 abort 当前请求。
- [ ] 在 `ModulePage.vue` 中使用同一个控制器执行解析/导出操作：
  - HTTP 超时后不直接失败，进入“确认中”。
  - 路由离开或组件卸载时调用 `cancel()`。
  - 重新进入页面时从资源状态恢复，不恢复旧按钮 loading。
- [ ] 扩展 `app-shell.test.js`：
  - `shouldStartFallback({ status: 504 }) === true`。
  - 业务码 `4094` 进入 fallback。
  - 控制器 cancel 后不会继续调用 poll。

### 3.4 Backend Workflow Tests

- [ ] 扩展 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`：
  - 已有 `shouldRejectParseWhenClaimParseStartFails` 保留。
  - 已有 `shouldRejectExportWhenNamedLockNotAcquired` 保留。
  - 新增 `shouldReturnExistingGraphRagExportWhenCompleteAndForceFalse`，mock `parseResultsService.hasCompleteGraphRagExport(...) == true`，断言不调用 orchestrator。
  - 新增 `shouldRunExportWhenForceTrueEvenIfCompleteExportExists`，断言调用 orchestrator 并释放命名锁。

### 3.5 Phase 2 Verification

- [ ] 执行：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

- [ ] 执行：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

- [ ] 手动接口检查：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/courses/os
curl --noproxy '*' http://127.0.0.1:8080/api/v1/courses/os/pdf-files
```

## 4. Phase 3 - Knowledge Bases And Build Wizard

### 4.1 Backend Knowledge Base Read APIs

- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/KnowledgeBaseQueryRequest.java`。
- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/KnowledgeBaseSummaryResponse.java`，字段包含 `id/courseId/kbCode/name/status/activeIndexRunId/description/latestIndexRunId/latestIndexRunStatus/createdAt/updatedAt`。
- [ ] 新建 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/KnowledgeBaseDetailResponse.java`，首版包含 Summary 全部字段，额外保留 `indexRunCount/successIndexRunCount/latestIndexRunMetadata`。
- [ ] 在 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/` 下新增 `KnowledgeBaseLookupService.java`：
  - 注入 `KnowledgeBasesService` 和 `IndexRunsService`。
  - 新增 `ApiPageData<KnowledgeBaseSummaryResponse> listKnowledgeBases(KnowledgeBaseQueryRequest request)`。
  - 新增 `KnowledgeBaseDetailResponse getKnowledgeBase(Long id)`。
  - 最近索引运行按 `IndexRuns.createdAt desc` 取第一条。
- [ ] 更新 `KnowledgeBasesController.java`：
  - 保留 `POST /{id}/index-runs`。
  - 保留 `GET /{id}/index-runs`。
  - 新增 `GET /api/v1/knowledge-bases`。
  - 新增 `GET /api/v1/knowledge-bases/{id}`。
  - 注意 `GET /{id}` 与 `GET /{id}/index-runs` 路由不冲突。
- [ ] 扩展 `KnowledgeBasesControllerWebMvcTest.java`：
  - 列表接口返回 `$.data.items[0].kbCode`。
  - 详情接口返回 `$.data.id` 和 `$.data.activeIndexRunId`。
  - 已有创建/列出索引运行测试保留。

### 4.2 Frontend Knowledge Bases API And Pages

- [ ] 新建 `frontend/apps/admin-app/src/api/knowledge-bases.js`，导出：
  - `listKnowledgeBases(params)`
  - `getKnowledgeBase(id)`
  - `listIndexRuns(id)`
  - `createIndexRun(id)`
  - `getIndexRun(id)`
- [ ] 更新 `module-content.js`：
  - `knowledge-bases.dataSource = 'live'`。
  - `knowledge-bases.rows = []`。
  - “新建知识库”禁用并设置 tooltip：“知识库创建接口未开放，当前请使用已有知识库完成构建联调。”。
  - `knowledge-base-detail.dataSource = 'live'`。
  - `knowledge-base-build.dataSource = 'live'`。
- [ ] 在 `module-loaders.js` 增加：
  - `knowledge-bases` table loader。
  - `knowledge-base-detail` overview loader。
  - `index-run-detail` overview loader。
- [ ] 更新 `ModulePage.vue`：
  - 知识库列表行点击进入 `/app/knowledge-bases/:kbId`。
  - `activeIndexRunId` 非空显示可问答；为空显示需先构建索引。
  - 知识库详情提供进入 `/app/knowledge-bases/:kbId/build` 的入口。

### 4.3 Build Wizard Five-Step Live Flow

- [ ] 在 `module-loaders.js` 增加 `knowledge-base-build` loader：
  - 先请求 `getKnowledgeBase(kbId)`。
  - 根据 `knowledgeBase.courseId` 请求 `listCourseMaterials(courseId)`。
  - 请求 `listIndexRuns(kbId)`。
  - 如果 URL query 存在 `materialId`，请求该资料详情和解析结果。
  - 首版只允许单选一个“本次构建主资料”。
- [ ] `workflowSteps` 状态映射：
  - `material`：已选择一个资料为 `done`，否则 `ready`。
  - `parse`：`parseStatus=done` 为 `done`，`processing` 为 `running`，`failed` 为 `failed`，未选择资料为 `blocked`。
  - `export`：完整 GraphRAG 产物存在为 `done`，导出确认中为 `running`，未解析完成为 `blocked`。
  - `index`：最近运行 `success` 且 `activeIndexRunId` 等于该运行 ID 为 `done`；最近运行 `running` 为 `running`；最近运行 `failed` 为 `failed`。
  - `smoke`：已有 QA 冒烟成功为 `done`；缺少激活索引为 `blocked`。
- [ ] 索引按钮调用 `createIndexRun(kbId)`，使用 `long-task-state.js` 的 index limits。
- [ ] 如果 `createIndexRun` HTTP 超时且前端没有拿到 `indexRunId`：
  - 调用 `listIndexRuns(kbId)`。
  - 取最近 `running` 或最近 `success` 运行。
  - 不重复 POST 创建索引。
- [ ] 索引成功后立即调用 `getKnowledgeBase(kbId)`，确认 `activeIndexRunId`。
- [ ] 移除 UI 中独立“激活索引版本”按钮和步骤。

### 4.4 Phase 3 Verification

- [ ] 执行：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

- [ ] 执行：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

- [ ] 手动接口检查：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/knowledge-bases
curl --noproxy '*' http://127.0.0.1:8080/api/v1/knowledge-bases/1
curl --noproxy '*' http://127.0.0.1:8080/api/v1/knowledge-bases/1/index-runs
```

## 5. Phase 4 - QA Smoke Verification

### 5.1 Frontend QA API

- [ ] 新建 `frontend/apps/admin-app/src/api/qa.js`，导出：
  - `createQaSession(payload)`
  - `sendQaMessage(sessionId, payload)`
  - `getQaTask(sessionId, taskId)`
- [ ] 新建 `frontend/apps/admin-app/src/views/pages/qa-polling.js`，导出：
  - `resolveQaPollingInterval(task, requestMode)`。
  - `resolveQaStaleTimeout(task, requestMode)`。
  - `isQaTerminalState(status)`。
- [ ] 轮询参数规则：
  - interval 优先使用 `recommendedPollingIntervalSeconds`。
  - stale 上限优先使用 `staleTimeoutSeconds`。
  - mode 来源优先级：任务响应 `mode` > 发送消息请求 mode > `local`。
  - `local/basic` 默认 `10s / 300s`。
  - `global/drift` 默认 `30s / 1800s`。
- [ ] 路由离开、组件卸载、用户取消时清理 QA 轮询 timer 和 AbortController。

### 5.2 Build Wizard QA Step

- [ ] 在 `knowledge-base-build` 的 `smoke` 步骤中增加真实操作：
  - 已有 `activeIndexRunId` 才允许发起。
  - 创建 QA session，`sessionType` 使用 `smoke`。
  - 发送固定默认问题“请用一句话概括当前知识库的主要内容。”，允许用户改写。
  - 轮询任务直到 `success/failed/timeout/stale`。
  - 成功后展示 assistant 消息摘要和进入会话详情的入口。
  - 失败时展示 Java 返回 `message` 和原始错误摘要入口。
- [ ] 不使用 mock 成功结果；如果联调环境缺索引，显示 `blocked`。

### 5.3 Phase 4 Verification

- [ ] 执行：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

- [ ] 执行：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

- [ ] 浏览器检查：
  - `/app/knowledge-bases/:kbId/build` 最后一步缺索引时 blocked。
  - 有 `activeIndexRunId` 时可发起 QA 冒烟。
  - 离开页面后 Network 面板不再继续轮询。

## 6. Cross-Cutting Error Handling

- [ ] `unwrapApiResponse()` 对非标准 envelope 抛：

```js
{
  message: '后端响应格式不符合 CKQA ApiResponse 契约',
  nonStandard: true,
  status,
  raw,
}
```

- [ ] 标准 envelope 但 `code !== 200` 时抛业务错误，保留 `code/message/data/status/raw`。
- [ ] 前端按以下业务策略处理：
  - HTTP `401`：跳转开发态登录页并保留返回目标。
  - HTTP `403`：显示无权限，不自动重试。
  - HTTP `404` 或业务码 `4044-4048`：主资源不存在回列表；子资源不存在显示区块错误。
  - `4093`：刷新资料详情，不重复 POST 解析。
  - `4094`：进入导出确认中，轮询结果列表。
  - `4095`：进入索引确认中，轮询索引运行列表。
  - `4096`：停止发送消息，提示重新创建会话。
  - `4097`：阻塞 QA 冒烟步骤。
  - `4000/4001`：展示参数或校验错误，不自动重试。
  - `5000/5003/5004`：通用错误面板 + 重试或查看日志入口。
- [ ] Phase 2 前用 WebMvc 测试确认相关接口确实返回 `4093-4097` 中的对应业务码。

## 7. CORS Strategy

- [ ] 首选后端受控 CORS，新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/WebCorsConfig.java`。
- [ ] 仅允许本地开发来源：
  - `http://127.0.0.1:*`
  - `http://localhost:*`
- [ ] 生产来源不在代码中硬编码；生产通过部署配置收紧。
- [ ] 如果项目改用 Vite proxy，则保持后端 CORS 关闭，并把 `VITE_API_BASE_URL` 设置为 `/api/v1`；同一环境不同时启用两种方案。
- [ ] `WebCorsConfig` 已在 Phase 1 落地；本节作为后续环境切换时的约束，不再重复实现。

## 8. Final Regression And Handoff

- [ ] 从仓库根目录执行：

```bash
git status --short
```

- [ ] 执行前端完整验证：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

- [ ] 执行后端完整验证：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

- [ ] 本地联调启动顺序：

```bash
cd graphrag_pipeline
python utils/main.py
```

```bash
cd backend/ckqa-back
./mvnw spring-boot:run
```

```bash
cd frontend/apps/admin-app
pnpm dev
```

- [ ] 本地联调接口：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/system/health
curl --noproxy '*' http://127.0.0.1:8080/api/v1/courses
curl --noproxy '*' http://127.0.0.1:8080/api/v1/knowledge-bases
```

- [ ] 浏览器验收：
  - `/app/health` 首屏自动出现真实健康状态。
  - `/app/courses` 不再出现静态 mock rows。
  - `/app/courses/:courseId` 可以看到真实资料和知识库。
  - `/app/knowledge-bases` 与 `/app/knowledge-bases/:kbId` 显示实时数据。
  - `/app/knowledge-bases/:kbId/build` 五步流程由真实接口推进或阻塞。
  - 已接入页面的 `DataSourceChip` 显示“实时数据”。
  - 未接入页面继续显示“示例数据”或“未开放”。

## 9. Non-Goals

- [ ] 不实现课程创建接口。
- [ ] 不实现知识库创建接口。
- [ ] 不实现资料上传 UI。
- [ ] 不做完整 RBAC 编辑器。
- [ ] 不让前端正式业务流直接请求 GraphRAG Python `/v1`。
- [ ] 不支持多资料一次构建；多资料能力需要新增批量导出接口、GraphRAG 输入合并/指定策略和索引运行元数据后再实现。

## 10. Commit Strategy

- [ ] Commit 1：后端课程列表/详情接口、本地 CORS 配置和测试。
- [ ] Commit 2：前端 API client、health 自动加载、courses live loader 和测试。
- [ ] Commit 3：课程详情、资料详情、长任务兜底和测试。
- [ ] Commit 4：后端知识库列表/详情接口和测试。
- [ ] Commit 5：知识库列表/详情、构建向导五步 live 流程和测试。
- [ ] Commit 6：QA 冒烟验证、最终回归和文档同步。
