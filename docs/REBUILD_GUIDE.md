# 成品之后如何自己重新实现

以当前成品的接口、表结构、页面和测试为参照，在你自己的练习目录从小系统逐步实现。保留成品仓库和数据库作为对照，不要直接清空成品代码或业务数据。

每一阶段都做到“写完、运行、验证、解释”再继续。遇到差异时先根据请求和数据库结果定位，最后再对照成品实现。

| 阶段 | 自己实现的内容 | 可验证的结果 | 成品参考 |
|---|---|---|---|
| 1 | PostgreSQL 表、实体、Mapper、参数校验和统一响应 | 能用真实数据库完成最小查询 | sql/schema.sql、backend 的 entity / mapper / dto |
| 2 | BCrypt、注册登录、JWT、USER/ADMIN 与资源权限 | 无 Token 为 401，越权为 403，本人资源可访问 | security、SecurityConfig、UserService、JwtService |
| 3 | 工单 CRUD、分页筛选和状态流转 | 用户建单，管理员按合法状态处理 | TicketService、controller、TicketBusinessTests |
| 4 | 评论、历史、事务、条件更新和请求幂等 | 并发不会重复建单，失败事务不留下错误历史 | TicketActivityService、TicketTransactionTests、TicketConcurrencyTests |
| 5 | 知识 CRUD/发布、关键词推荐及 Vue 界面 | 从浏览器完成登录、建单、处理、查知识的闭环 | ArticleService、frontend/src、api_smoke.py、ui_smoke.cjs |
| 6 | Redis 缓存、Lua 限流、撤销令牌与可靠消息 | 缓存按权限隔离；重复消息只记一次审计 | RateLimitService、TokenBlacklistService、OutboxService、InfrastructureTests |
| 7 | 切片、Embedding、pgvector 和 RAG | 只引用已发布且版本一致的知识；模型故障明确报错 | PublishedKnowledgeIndex、AssistantController、AssistantTests |
| 8 | 独立知识服务、数据库账号隔离、网关与注册发现 | 两个实例都接到请求，跨库访问被拒绝 | knowledge-service、gateway、registry、distributed_smoke.py |
| 9 | Redis/RabbitMQ 集群、容器与 Kubernetes | 实际停止节点、替换 Pod，并验证业务恢复 | docker/distributed、deploy/kubernetes、相关 smoke 脚本 |
| 10 | 压测、双库恢复、正式 HTTPS 与成品打包 | 生成可复核的报告、ZIP、逐文件校验清单 | CAPACITY_GUIDE、DISTRIBUTED_RECOVERY、FORMAL_DEPLOYMENT |

第一遍重点是把基础单体业务实现完整，随后逐步加入中间件和分布式模式。每完成一个阶段保留一次自己的 Git 提交，记录当时的问题与验证结果；这样面试时可以解释自己的实现过程，而不只是复述最终代码。

统一验收入口：`pwsh ./scripts/verify-product.ps1 -LocalInfrastructure`。它需要本机已有原开发基础设施；全新电脑可先按 README 准备环境，或通过成品包的正式部署入口运行并观察完整系统。真实 AI 模型需要单独准备。
