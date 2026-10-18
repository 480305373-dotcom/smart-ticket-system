# 完整版交付报告

交付日期：2026-10-18。本轮继续修改原 Spring Boot / Vue 项目；原有功能与本机数据保留，没有重建项目或删除数据库。

## 已完成的业务与技术

- 保留登录、JWT、BCrypt、工单权限、CRUD、筛选分页、指派、评论、历史、知识发布和关键词推荐。
- 接通真实 Redis：JSON 缓存、事务提交后的缓存变更、原子分布式限流、失败登录保护、摘要 key 与 TTL。
- JWT 撤销持久化到 PostgreSQL，Redis 数据丢失不会恢复旧 Token。改密、停用与角色调整立即使旧会话失效。
- 完成管理员创建账号、角色/启用管理、自助注册入口、账号安全页；停用/降级处理人时自动释放未完成工单指派并写历史。
- 工单创建、处理历史与 event_outbox 在同一事务提交；RabbitMQ 确认、不可路由检查、租约认领、退避重试、失败事件重试、数据库消费去重与安全死信重放。
- 新增运维、操作审计、用户管理页面，展示真实服务和事件状态。
- 接通真实 Ollama + nomic embedding + pgvector 问答。仅已发布且当前版本一致的文章进入提示词；结构化输出最终回答，显示出处链接。
- 提供 Actuator、请求 ID、HTTP/JVM/事件指标、Prometheus/Grafana 配置、独立监控认证、Docker/Nginx、多阶段和预构建部署、CI 与私有备份脚本。

没有将关键词推荐包装为大模型；旧 /ai 路径保留，并统一修复模型空正文和思考草稿问题。工单优先级建议仍明确采用规则，最终创建由用户确认。

## 验证证据

| 项目 | 本机结果 |
|---|---|
| 后端 clean package | 成功，32 个测试全部通过，0 失败/错误/跳过 |
| 前端安装与 build | 成功，npm audit 0 vulnerabilities |
| 前端时间处理 | 3 个 Node 测试通过，验证 UTC、跨日及本地时间显示 |
| 业务真实 HTTP | 69 个业务检查通过，临时资源已清理 |
| 完整模式真实 HTTP | 12 个检查通过，包括真实模型答案与出处 |
| 主浏览器流程 | 22 个流程通过，页面脚本错误 0 |
| 成品页面账号流程 | 创建账号、改密、旧 Token 401、新密码登录、停用会话失效、可见 AI 回答均验证 |
| PostgreSQL | 原本机库与完整模式容器库都已实际使用；SQL 为幂等增量结构 |
| 备份与恢复 | 归档校验成功；已在独立临时容器执行 pg_restore，五张核心表数量与源库一致 |
| Docker / Nginx | 本机完整容器部署通过；69 项业务接口、22 个主浏览器流程及 5 个账号/AI 页面流程在 8088 成品入口复验 |
| Prometheus / Grafana | GitHub Actions 实际启动并验收：专用认证采集 UP、JVM 指标存在、四面板仪表盘及数据源健康 |

真实结果文件位于 docs/api-verification.json、enterprise-verification.json、ai-verification.json、ui-verification.json、account-ui-verification.json。截图来自实际页面。样例账号测试只删除本轮临时账号和其专属审计，不操作已有用户。

测试覆盖：跨用户权限、完整生命周期、并发状态更新、并发重复提交、历史失败回滚、账号会话版本、指派释放、Redis 并发窗口与 TTL、缓存权限隔离、Token 缓存丢失、消息确认和消费去重、消费失败回滚、死信持久化恢复、草稿/过期知识不得进入模型、空回答必须报错。

## 运行与交付文件

完整启动、正式管理员、监控认证、排错和备份恢复步骤见 [ENTERPRISE_GUIDE.md](ENTERPRISE_GUIDE.md)。本机运行命令：

```powershell
.\scripts\start-enterprise.ps1 -WithAi
```

成品容器入口：http://127.0.0.1:8088；本机开发入口：http://127.0.0.1:5173。演示普通账号 dev_zhangsan，管理员 dev_admin1，密码均为 Demo@12345。本机完整模式使用 Docker PostgreSQL 5433；独立成品容器另用私有数据卷，原本机 PostgreSQL 5432 不被替换。

自助注册在 dev 开启，在 prod 默认关闭；登录页按公开设置显示入口，直接访问关闭的注册页会提示联系管理员。管理员仍可在“用户与权限”创建账号。

发布包包含源码、后端可执行 JAR、前端 dist、SQL、部署脚本、指南与逐文件校验清单；另附 ZIP SHA-256。排除真实密码、运行会话、备份、node_modules、日志和个人文档。`.runtime/production.env` 等私有配置仅留在本机，不随发布包分发。

## 验收边界

- 本机 Java/Vue + PostgreSQL/Redis/RabbitMQ/Ollama/pgvector 链路已经真实验收。
- 本机已启动独立业务容器与真实 AI；本机尚未启动 Prometheus/Grafana，完整监控栈已在 GitHub CI 实际运行验证。可按指南使用 `-Monitoring` 启动本机监控。
- 已在独立临时容器完成恢复演练，证据为 restore-verification.json；没有覆盖业务原库。
- GitHub Actions 已实际完成后端 32 项测试、前端 3 项测试及构建、完整容器和监控验收。
- 本轮没有绑定公网域名、申请证书或部署到付费服务器，没有提供未经测量的吞吐量/延迟数字。

## 部署验收记录

本机 `smartticket-production` 项目中的 PostgreSQL、Redis、RabbitMQ、后端和 Nginx 前端五项容器均为 healthy。最终 JAR 与 dist 采用预构建目标装入镜像；源码构建目标保留。通过 8088 的真实 AI 请求与浏览器回答验证了容器到宿主机 Ollama 的连接。

`scripts/deployment_smoke.py` 验证 SPA 深链接、readiness、正式注册关闭、三项真实中间件、普通用户权限、独立监控认证、创建幂等和异步审计；带 `--monitoring` 时还验证 Prometheus backend scrape UP、实际 JVM 时序、Grafana 数据库、预置四面板及数据源查询健康。

监控完整链路的已完成云端记录：[GitHub Actions 39114634765](https://github.com/480305373-dotcom/smart-ticket-system/actions/runs/39114634765)。原始验收产物保存为 monitoring-verification.json；最终分支提交仍会再执行完整工作流。本机结果保存为 deployment-verification.json。监控密码、初始管理员密码和 JWT 密钥均为部署时随机生成，不随源码分发。

## 代码导读

先按 README 阅读 Entity → Mapper → Service → Controller → Security → Vue。进阶再读 RateLimitService / TokenBlacklistService、TicketService → TicketEventPublisher → OutboxService → TicketAuditListener → DeadLetterRecoveryService，然后阅读 PublishedKnowledgeIndex → AssistantController → AiTextService、监控配置、Dockerfile 与 CI。所有技术都对应可运行的业务入口和测试，避免只记组件名称。
