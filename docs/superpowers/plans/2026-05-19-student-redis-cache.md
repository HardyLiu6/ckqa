# CKQA 学生端 Redis 缓存接入计划

日期：2026-05-19

状态：Implemented

## Summary

为学生端 Java `/api/v1` 读路径接入 Redis 服务端缓存。浏览器不直连 Redis，student-app 仍只调用 Java API；Redis 只缓存低风险、可按 TTL 失效的读模型和智能推荐结果，不缓存问答消息、task 轮询结果或最终 assistant answer。

## Scope

已落地缓存范围：

1. `GET /api/v1/courses`
   - 按当前登录 `userCode` 与分页/筛选参数隔离。
   - 默认 TTL `CKQA_STUDENT_CACHE_COURSE_TTL=PT60S`。
2. `GET /api/v1/courses/{courseId}/knowledge-bases`
   - 先校验课程可读，再按 `userCode + courseId` 隔离。
   - 默认 TTL `CKQA_STUDENT_CACHE_COURSE_KB_TTL=PT60S`。
3. `POST /api/v1/qa-routing/recommend`
   - 先校验登录、session owner、course/knowledgeBase 权限，再缓存纯路由结果。
   - 默认 TTL `CKQA_STUDENT_CACHE_ROUTING_TTL=PT5M`。
4. `POST /api/v1/qa-sessions/hybrid-warmup`
   - 先校验课程/知识库权限和 active index。
   - 按 `knowledgeBaseId + activeIndexRunId + dataDirUri` 缓存 readiness。
   - ready 默认 TTL `PT5M`，not ready 默认 TTL `PT15S`。

## Safety

1. 使用 `StringRedisTemplate + ObjectMapper` 存 JSON，不使用 Java 原生序列化。
2. Redis 读写、反序列化、SCAN 清理失败时全部 fail-open 回源。
3. 不缓存 401、403 和业务错误。
4. 管理端课程/知识库修改和 active index 切换会触发学生端课程/知识库缓存清理。
5. `/api/v1/system/health` 增加 `redis` 子项；Redis 不可达不改变 MySQL / GraphRAG 作为事实源的边界。

## Verification

建议验证：

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
docker compose --env-file infra/.env -f infra/docker-compose.yml exec -T redis redis-cli PING

cd backend/ckqa-back
./mvnw test

cd frontend/apps/student-app
node --test tests/*.test.js
pnpm build
```
