# 面向课程的混合型问答系统数据库设计

- 日期：2026-04-20
- 范围：`pdf_ingest/`、`graphrag_pipeline/`、`frontend/apps/student-app/` 对应的课程解析、知识库索引、用户管理与问答业务主链路
- 目标：在不破坏现有 `pdf_ingest -> graphrag_pipeline` 工作流的前提下，为“面向课程的混合型问答系统”设计一套最小可运行、可追踪、可扩展的数据库方案

## 1. 背景

当前仓库的真实主链路已经比较明确：

1. `pdf_ingest` 负责课程 PDF 上传、解析、标准化导出与 GraphRAG 输入产物生成。
2. `graphrag_pipeline` 负责从 MinIO / 本地目录拉取 JSON 输入、执行 `graphrag index`、并通过 `utils/main.py` 提供 OpenAI 兼容问答接口。
3. `frontend/apps/student-app/` 已经显式预留了课程问答、历史会话、知识来源展示等交互，但仍以本地状态和示例数据为主，尚未接入稳定后端契约。

当前 MySQL 只覆盖了解析链路中的基础元数据：

- `courses`
- `pdf_files`
- `parse_results`
- `parse_logs`

这满足了 PDF 解析和导出管理，但还不能支撑一套真正可运营、可追踪的课程问答系统。缺失的核心包括：

1. 用户、角色、权限与认证身份
2. 课程知识库实例与索引版本映射
3. 标准化文档与问答命中文档之间的结构化关联
4. 会话、消息、检索过程与错误追踪

因此，本设计的重点不是替换现有 GraphRAG 文件链路，而是在其之上补齐“业务事实库”。

## 2. 设计目标与边界

## 2.1 设计目标

本次设计只追求“最小可运行 + 面向后续扩展”的数据库方案，优先支撑以下能力：

1. 管理用户与最小 RBAC 结构
2. 维持现有课程、PDF、解析结果、标准化导出与 GraphRAG 索引链路
3. 显式建模课程知识库与索引版本
4. 支撑问答会话、多轮消息、检索日志、命中文档追踪
5. 为后续接入更完整的课程平台能力留出清晰扩展口

## 2.2 明确不做

本次设计刻意不展开以下业务域：

1. 选课 / 班级 / 教学班
2. 学习进度 / 课时完成率
3. 收藏 / 点赞 / 反馈 / 举报
4. 社区帖子 / 评论 / 关系网络
5. 把全文知识内容或向量数据强行存进 MySQL

这些能力会通过可扩展字段、预留外键和 RBAC 结构支持后续平滑扩展，但不会进入本版最小模型。

## 3. 方案选型

## 3.1 不采用：关系型数据库尽量全收口

把标准化文档全文、GraphRAG 输入、索引运行细节、问答历史全部压入 MySQL 的优点是“看起来集中”，但会直接冲击当前已经跑通的文件工作流：

1. `pdf_ingest` 当前天然产出 `normalized_docs.json`、`section_docs.json`、`page_docs.json`
2. `graphrag_pipeline` 当前天然消费 `input/*.json`、`output/*.parquet` 与 `output/lancedb/`
3. 把这套链路强制改造成“完全由数据库承载”会明显超出最小可运行范围

## 3.2 采用：关系型业务库 + 外部对象/索引存储

采用分层方案：

1. **对象/文件层**
   - 继续存放 PDF 原件、MinerU 解析结果、标准化 JSON、GraphRAG 输入和索引产物
   - 当前仍由 MinIO / 本地文件系统承担
2. **关系型业务库**
   - 存放用户、角色、课程、知识库、索引版本、会话、消息、检索日志等结构化事实
3. **检索/索引运行层**
   - 继续由 GraphRAG 输出目录和 LanceDB 承担实际检索

这是当前最稳妥、最低复杂度、也最符合仓库现状的方案。

## 3.3 不采用：提前做多库拆分

将用户库、知识库、检索观测库彻底拆分虽然长期更“纯”，但明显超出当前项目阶段，会过早引入开发、联调和运维复杂度。

## 4. 总体分层设计

本系统的数据职责按下列三层划分：

## 4.1 对象/文件层

负责保存大对象、半结构化文件和 GraphRAG 原生产物：

1. 原始 PDF
2. MinerU 解析 JSON
3. `normalized_docs.json`
4. `section_docs.json` / `page_docs.json`
5. GraphRAG `output/`、`reports/`、`lancedb/`

这层不要求“强关系化”，但需要在数据库中保存路径、状态、版本和映射关系。

## 4.2 关系型业务库

负责保存“谁、什么时候、基于什么知识、问了什么、命中了什么、结果如何”的结构化事实。该层是本次设计的重点。

## 4.3 检索/索引运行层

继续让 GraphRAG CLI 与 LanceDB 承担实际检索执行。数据库不复制内部索引结构，只保存：

1. 当前会话使用的知识库
2. 当前知识库激活的是哪次索引运行
3. 某次回答使用了哪个 query mode
4. 命中了哪些文档片段

## 5. 领域模型与表分组

本次设计将表拆成三组：用户与权限域、课程与知识资产域、问答业务域。

## 5.1 用户与权限域

### `users`

用户主表，存放平台内部用户主体。

建议核心字段：

- `id bigint pk`
- `user_code varchar(64) unique`
- `username varchar(64) unique`
- `email varchar(255) unique nullable`
- `phone varchar(32) unique nullable`
- `display_name varchar(128)`
- `password_hash varchar(255)`
- `status enum('active','disabled','locked','pending')`
- `last_login_at timestamp nullable`
- `extra_metadata json nullable`
- `created_at`
- `updated_at`
- `is_deleted`

说明：

1. `user_code` 用于对外暴露稳定业务标识，避免直接透出数据库主键
2. 首版仍支持本地账号登录，因此 `password_hash` 保留在 `users`
3. 更复杂的认证信息进入 `auth_identities`

### `roles`

角色表。首版只预置：

1. `user`
2. `admin`

建议字段：

- `id bigint pk`
- `role_code varchar(64) unique`
- `role_name varchar(128)`
- `description text`
- `created_at`
- `updated_at`

### `permissions`

权限点表，用于后续扩展细粒度授权。

建议字段：

- `id bigint pk`
- `permission_code varchar(128) unique`
- `permission_name varchar(128)`
- `description text`

### `user_roles`

用户角色关联表。

建议字段：

- `id bigint pk`
- `user_id bigint fk -> users.id`
- `role_id bigint fk -> roles.id`
- `created_at`

唯一约束：

- `(user_id, role_id)` 唯一

### `role_permissions`

角色权限关联表。

建议字段：

- `id bigint pk`
- `role_id bigint fk -> roles.id`
- `permission_id bigint fk -> permissions.id`
- `created_at`

唯一约束：

- `(role_id, permission_id)` 唯一

### `auth_identities`

认证身份扩展表，用于兼容本地账号、OAuth、微信、学校统一认证等多种登录方式。

建议字段：

- `id bigint pk`
- `user_id bigint fk -> users.id`
- `provider varchar(32)`，例如 `local` / `wechat` / `oauth` / `sso`
- `provider_user_id varchar(255)`
- `identity_key varchar(255)`，可用于存统一标识
- `credential_meta json nullable`
- `created_at`
- `updated_at`

唯一约束：

- `(provider, provider_user_id)` 唯一

说明：

1. 首版只需要写入 `provider='local'`
2. 后续接第三方登录时无需改 `users`

## 5.2 课程与知识资产域

### 保留现有表

以下表保留并继续作为解析链路事实来源：

1. `courses`
2. `pdf_files`
3. `parse_results`
4. `parse_logs`

这四张表已经与 `pdf_ingest/scripts/pdf_processor/db_service.py` 和 `pdf_ingest/sql/ocqa.sql` 对齐，不应推翻，只做增量增强。

### `knowledge_bases`

课程知识库主表。它不是单条文档，而是“某门课程面向问答使用的一套知识库实例”。

建议字段：

- `id bigint pk`
- `kb_code varchar(64) unique`
- `course_id varchar(64) fk -> courses.course_id`
- `name varchar(255)`
- `description text nullable`
- `status enum('draft','ready','disabled')`
- `active_index_run_id bigint nullable`
- `created_at`
- `updated_at`
- `extra_metadata json nullable`

说明：

1. 一门课程可以存在多个知识库实例，但首版建议每门课默认只有一个主知识库
2. 保留该层是为了把“课程”和“当前激活索引版本”解耦
3. `active_index_run_id` 在实施时应建为指向 `index_runs.id` 的可空外键，并由服务层保证该 run 属于当前 `knowledge_base`

### `kb_documents`

知识文档元数据表，对应标准化文档或 GraphRAG 投影文档单元。

建议字段：

- `id bigint pk`
- `knowledge_base_id bigint fk -> knowledge_bases.id`
- `source_pdf_file_id bigint nullable fk -> pdf_files.id`
- `doc_id varchar(255)`
- `doc_unit enum('normalized','section','page')`
- `document_type varchar(32)`
- `title varchar(512) nullable`
- `heading_level int nullable`
- `heading_path_text varchar(1024) nullable`
- `page_start int nullable`
- `page_end int nullable`
- `source_file varchar(255)`
- `storage_bucket varchar(64) nullable`
- `storage_object_key varchar(512) nullable`
- `summary_text text nullable`
- `extra_metadata json nullable`
- `created_at`
- `updated_at`

唯一约束：

- `(knowledge_base_id, doc_id)` 唯一

说明：

1. 该表不强制存文档全文
2. `storage_object_key` 指向 MinIO / 文件系统中的真实来源
3. 该表的核心职责是“建立结构化溯源与问答命中映射”

### `index_runs`

一次知识库索引构建记录，对应一次 `graphrag index --root .`。

建议字段：

- `id bigint pk`
- `knowledge_base_id bigint fk -> knowledge_bases.id`
- `run_version varchar(64)`
- `status enum('pending','running','succeeded','failed','cancelled')`
- `trigger_type enum('manual','scheduled','rebuild','sync')`
- `started_at timestamp nullable`
- `finished_at timestamp nullable`
- `error_message text nullable`
- `report_path varchar(512) nullable`
- `extra_metadata json nullable`
- `created_at`
- `updated_at`

唯一约束：

- `(knowledge_base_id, run_version)` 唯一

### `index_artifacts`

索引产物表，记录某次 `index_runs` 的可用产物。

建议字段：

- `id bigint pk`
- `index_run_id bigint fk -> index_runs.id`
- `artifact_type enum('input_json','documents_parquet','entities_parquet','relationships_parquet','reports','lancedb','other')`
- `storage_uri varchar(1024)`
- `checksum varchar(128) nullable`
- `status enum('created','available','missing','expired')`
- `is_active tinyint(1) default 0`
- `created_at`
- `updated_at`

说明：

1. 一次索引可能关联多个产物
2. `knowledge_bases.active_index_run_id` 决定线上问答使用哪次索引版本，而不是依赖最新产物自动覆盖

## 5.3 问答业务域

### `qa_sessions`

问答会话表，对应前端的“新对话 / 历史对话 / 继续追问”。

建议字段：

- `id bigint pk`
- `session_code varchar(64) unique`
- `user_id bigint fk -> users.id`
- `course_id varchar(64) nullable fk -> courses.course_id`
- `knowledge_base_id bigint nullable fk -> knowledge_bases.id`
- `title varchar(255)`
- `status enum('active','archived','deleted')`
- `last_message_at timestamp nullable`
- `created_at`
- `updated_at`
- `extra_metadata json nullable`

说明：

1. `course_id` 允许为空，以兼容未来平台级通用问答
2. `knowledge_base_id` 允许为空，以允许会话延后绑定具体课程知识库
3. 若两者同时存在，`knowledge_base_id` 对应的课程必须与 `course_id` 一致

### `qa_messages`

问答消息流表，统一存储用户消息、助手消息和系统消息。

建议字段：

- `id bigint pk`
- `session_id bigint fk -> qa_sessions.id`
- `seq_no int`
- `role enum('system','user','assistant')`
- `content longtext`
- `status enum('pending','generating','completed','failed')`
- `token_count int nullable`
- `latency_ms int nullable`
- `error_message text nullable`
- `created_at`
- `updated_at`
- `extra_metadata json nullable`

唯一约束：

- `(session_id, seq_no)` 唯一

说明：

1. 用户消息通常创建即 `completed`
2. 助手消息可以先写占位，再从 `generating` 更新为 `completed` 或 `failed`

### `qa_retrieval_logs`

检索执行日志表，用来记录一次助手回答背后的检索动作。

建议字段：

- `id bigint pk`
- `assistant_message_id bigint fk -> qa_messages.id`
- `index_run_id bigint nullable fk -> index_runs.id`
- `query_mode enum('local','global','full')`
- `query_text text`
- `hit_count int default 0`
- `latency_ms int nullable`
- `status enum('running','succeeded','failed')`
- `error_message text nullable`
- `created_at`
- `updated_at`
- `extra_metadata json nullable`

说明：

1. 检索日志挂在“助手消息”下，而不是直接挂会话
2. 这样最符合“一次回答对应一次检索执行”的排障视角
3. 后续若采用复杂编排，也允许一条助手消息对应多条 retrieval log
4. `assistant_message_id` 在业务层必须保证其引用的是 `role='assistant'` 的消息记录

### `qa_retrieval_hits`

检索命中明细表，对应一次检索中命中的文档片段。

建议字段：

- `id bigint pk`
- `retrieval_log_id bigint fk -> qa_retrieval_logs.id`
- `kb_document_id bigint nullable fk -> kb_documents.id`
- `rank_no int`
- `score decimal(10,6) nullable`
- `snippet text nullable`
- `source_file varchar(255) nullable`
- `page_start int nullable`
- `page_end int nullable`
- `created_at`
- `extra_metadata json nullable`

说明：

1. `kb_document_id` 允许为空，是为了兼容极端情况下仅拿到片段摘要、未成功映射文档元数据的情形
2. 正常链路中仍应优先落到 `kb_documents`

## 6. 核心关系与主外键规则

核心 ER 主链路如下：

1. `users` 1 -> N `qa_sessions`
2. `qa_sessions` 1 -> N `qa_messages`
3. `users` N -> N `roles`，通过 `user_roles`
4. `roles` N -> N `permissions`，通过 `role_permissions`
5. `users` 1 -> N `auth_identities`
6. `courses` 1 -> N `pdf_files`
7. `pdf_files` 1 -> N `parse_results`
8. `courses` 1 -> N `knowledge_bases`
9. `knowledge_bases` 1 -> N `kb_documents`
10. `knowledge_bases` 1 -> N `index_runs`
11. `index_runs` 1 -> N `index_artifacts`
12. `qa_sessions` N -> 1 `courses`（可空）
13. `qa_sessions` N -> 1 `knowledge_bases`（可空）
14. `qa_messages` 1 -> N `qa_retrieval_logs`
15. `qa_retrieval_logs` 1 -> N `qa_retrieval_hits`
16. `qa_retrieval_hits` N -> 1 `kb_documents`
17. `qa_retrieval_logs` N -> 1 `index_runs`

需要特别强调的三条规则：

1. **同一知识库同一时刻只应有一个激活索引版本**
2. **同一会话中的消息顺序必须由 `(session_id, seq_no)` 唯一保证**
3. **问答系统的检索命中应尽量映射到 `kb_documents`，而不是只存无来源纯文本**

补充一致性约束：

1. `knowledge_bases.active_index_run_id` 必须指向本知识库自己的某次 `index_runs`
2. `qa_sessions` 若同时绑定课程与知识库，则二者必须属于同一课程
3. `qa_retrieval_logs.assistant_message_id` 必须引用助手消息，而不是用户消息

## 7. 状态流设计

## 7.1 解析与知识库状态

### `pdf_files`

沿用现有状态：

- `pending -> processing -> done / failed`

### `knowledge_bases`

建议状态：

- `draft -> ready -> disabled`

说明：

1. `draft`：已建立知识库，但尚无可用索引版本
2. `ready`：至少有一版成功索引，可提供问答
3. `disabled`：人工停用，不再承接新会话

### `index_runs`

建议状态：

- `pending -> running -> succeeded / failed / cancelled`

说明：

1. 新索引构建成功后不直接覆盖线上版本
2. 由业务逻辑显式切换 `knowledge_bases.active_index_run_id`
3. 这样可以支持回滚、灰度比较和版本追溯

### `index_artifacts`

建议状态：

- `created -> available / missing / expired`

## 7.2 问答状态

### `qa_sessions`

建议状态：

- `active -> archived -> deleted`

### `qa_messages`

建议状态：

- `pending -> generating -> completed / failed`

规则：

1. 用户消息通常直接写入 `completed`
2. 助手消息先写占位记录，状态为 `generating`
3. GraphRAG 查询成功后更新消息正文与状态
4. 查询失败时保留失败消息与错误原因

### `qa_retrieval_logs`

建议状态：

- `running -> succeeded / failed`

## 8. 核心业务时序

一次标准课程问答建议按以下顺序写入数据库：

1. 前端发起问题
2. 创建或定位 `qa_sessions`
3. 插入用户消息到 `qa_messages`
4. 插入一条助手占位消息，状态为 `generating`
5. 根据会话上下文定位 `course_id`、`knowledge_base_id` 与当前 `active_index_run_id`
6. 调用 GraphRAG 执行 `local` / `global` / `full` 查询
7. 写入 `qa_retrieval_logs`
8. 写入 `qa_retrieval_hits`
9. 更新助手消息内容、耗时与状态
10. 更新 `qa_sessions.last_message_at`

该时序的目的有两个：

1. 能完整还原一条对话历史
2. 能在出现错误时快速定位是“无可用索引”“检索失败”“命中为空”还是“消息更新失败”

## 9. 关键建模原则

## 9.1 双标识设计

新增核心表建议统一采用：

1. `id bigint` 作为内部主键
2. `*_code` 作为稳定业务标识

这样既保证关联效率，也方便未来 API 与前端使用稳定 ID。

## 9.2 审计字段统一

新增业务表建议统一保留：

1. `created_at`
2. `updated_at`
3. `status`
4. `extra_metadata json`

其中：

- `status` 用于状态流与排障
- `extra_metadata` 用于延后承接尚未稳定的扩展字段

## 9.3 不过度关系化

MySQL 中只保存“足够支撑业务、回溯与排障”的结构化字段，不把全文内容、向量数据和 GraphRAG 内部实现细节强行转换成关系模型。

## 10. 错误处理原则

问答系统不应把失败记录全部回滚掉。建议规则如下：

1. **检索失败不回滚用户消息**
2. **助手失败消息保留**
3. **检索日志尽量保留**
4. **命中文档日志只要能写入，就不应因后续回答失败而被抹掉**

理由：

1. 对话失败记录本身就是排障资产
2. 课程问答系统更接近“可观测工作流”，而不是纯事务型系统

同时将错误分为三类：

1. **可恢复错误**
   - 例如查询超时、索引文件暂不可读
   - 保留失败记录，允许用户重试
2. **业务阻断错误**
   - 例如课程没有可用 `active_index_run_id`
   - 直接写入明确错误提示，不进入检索
3. **一致性错误**
   - 例如 retrieval hit 映射到不存在的文档
   - 应优先用外键、唯一约束和状态检查提前阻断

## 11. 后续扩展口

虽然本次不实现完整课程平台，但数据库应明确为后续扩展留口：

1. `qa_sessions.course_id` 可空
   - 允许平台级通用问答与课程内问答共存
2. `auth_identities`
   - 支持未来接入微信、OAuth、校园统一认证
3. `roles / permissions`
   - 支持未来扩展学生、教师、助教、运营等多角色体系
4. `kb_documents.extra_metadata`
   - 允许后续扩展表格数、公式数、图片数、chunk 策略
5. `qa_messages.extra_metadata`
   - 允许后续扩展附件、模型参数、上下文窗口、审核信息

这样后续再引入选课、学习进度、收藏/反馈等能力时，不需要推翻本次主结构。

## 12. 验证策略

后续正式实施时，建议按四层验证：

## 12.1 表结构验证

检查：

1. 唯一键是否覆盖核心业务键
2. 外键方向是否符合主链路
3. 状态枚举是否满足运行态要求
4. 可空字段是否与业务边界一致

## 12.2 状态流验证

至少验证以下场景：

1. PDF 解析成功后能关联到知识库
2. 索引运行成功后能切换 `active_index_run_id`
3. 问答失败后仍保留用户消息与失败日志

## 12.3 链路回放验证

至少能做到：

1. 从一条助手消息追到检索日志
2. 从检索日志追到命中文档
3. 从命中文档追到知识库与源 PDF
4. 从会话追到所用索引版本

## 12.4 兼容性验证

必须确保：

1. 不破坏现有 `pdf_ingest` 的解析链路
2. 不强迫 GraphRAG 直接读 MySQL
3. 现有 `input/*.json -> output/` 的工作方式保持可用

## 13. 风险与边界

本设计仍有以下已知边界：

1. 首版没有把用户组织、班级、课程成员关系纳入模型
2. `kb_documents` 保存的是“文档元数据 + 溯源锚点”，不是全文搜索库
3. 如果后续实际问答编排不再是“一次助手消息对应一次检索”，则需要扩展 `qa_retrieval_logs` 的编排字段
4. 若未来引入多租户或多校区隔离，还需在 `users`、`courses`、`knowledge_bases` 之上补充租户域

这些边界不影响本次最小可运行设计成立。

## 14. 结论

本次数据库设计采用：

**关系型业务库 + 外部对象/索引存储** 的分层方案。

其核心价值在于：

1. 不破坏现有 `pdf_ingest -> graphrag_pipeline` 主链路
2. 用最小复杂度补齐用户、知识库、索引版本、会话、消息、检索命中这些业务事实
3. 让系统能够明确回答：
   - 这是谁问的
   - 问的是哪门课
   - 用的是哪版知识库
   - 走了哪种查询模式
   - 命中了哪些文档
   - 最终结果是否成功

这套结构已经足以作为下一步实施计划与建表迁移设计的基线。
