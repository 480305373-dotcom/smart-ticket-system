# 稳定成品交付与验收

日期：2026-10-20。本次按“容量压测 → 分布式双库恢复 → 独立正式 HTTPS 部署 → 成品封装”的顺序补齐交付。原有业务代码、数据库与演示数据保留，未重建项目。

## 当前运行入口

- 本机正式环境：`https://localhost:8443`；HTTP `http://localhost:8089` 跳转至 HTTPS。
- 使用独立 `smartticket-formal` 数据卷与私密凭据，不导入开发演示账号。管理员凭据仅在本机 `.runtime/formal.env`。
- 已启用真实 Ollama/pgvector 问答；模型需要宿主机提供，不随 ZIP 分发。
- 原 8090 分布式演示环境当前停止以释放 Docker 内存，全部数据卷保留。原开发模式及 8088 部署仍可按原指南使用。

## 容量基线

本机 Windows / Docker，共享宿主机资源；核心服务 2 实例、知识服务 2 实例。数据集为 8 个账号、41 条工单及既有知识文章。每档持续 20 秒，总计 53158 次测量请求。

| 并发连接 | 成功请求/秒 | 成功请求 P95 | 错误率 |
|---:|---:|---:|---:|
| 1 | 94.96 | 15.17 ms | 0% |
| 4 | 419.12 | 13.58 ms | 0% |
| 8 | 910.28 | 12.12 ms | 0% |
| 16 | 1231.79 | 21.75 ms | 0% |

正常保护单独验证得到 120 次成功与 15 次 HTTP 429。测量阶段仅临时提高阈值，认证、权限及 Redis Lua 限流仍执行，结束后已恢复正常配置。采用闭环读取、小数据集和缓存环境；上述数字不是生产最大容量，不包含写入或 AI 吞吐量。

完整环境、资源快照及尾延迟：[capacity-verification.json](capacity-verification.json)。重新运行方式见 [容量指南](CAPACITY_GUIDE.md)。CI 会在自己的运行环境重新建立基线，不能将 CI 数字与本机数字混为同一环境。

## 双库恢复

- 8090 分布式库：核心库 14 张表、知识库 1 张表恢复成功，所有 public 表内容摘要、行数和序列状态一致。
- 独立正式库另行完成备份与恢复演练，覆盖 AI 框架实际使用的额外数据库扩展。
- 恢复后应用账号可查询自己的业务表，核心与知识账号互相连接对方数据库均被拒绝。
- 临时恢复容器无网络、无端口，不覆盖源库，验证后清理；备份仍留在私密 `.runtime/backups/`。

证据：[分布式恢复](distributed-restore-verification.json)、[正式库恢复](formal-restore-verification.json)。维护窗口及消息系统边界见 [恢复指南](DISTRIBUTED_RECOVERY.md)。

## 正式环境与构建

| 验证 | 本机结果 |
|---|---|
| 后端真实数据库/Redis/RabbitMQ 测试与构建 | 43 项，失败/错误/跳过均为 0；4 个可执行模块构建成功 |
| 前端 | 3 项测试与生产构建通过 |
| 交付工具 | 7 项测试通过，包含错误吞吐量统计和损坏备份拒绝 |
| HTTPS 与正式业务 | 23 项检查通过，含证书链、域名、权限、幂等、生命周期、异步审计和真实 RAG 出处 |
| 正式浏览器 | 10 项流程通过，无页面脚本错误，包含管理员/普通用户流程和基础响应式 |

构建证据：[product-build-verification.json](product-build-verification.json)。正式 API 与浏览器证据：[formal-verification.json](formal-verification.json)、[formal-ui-verification.json](formal-ui-verification.json)。证书校验由正式 API 验收独立完成；UI 测试的本机证书例外仅存在于测试浏览器上下文。

GitHub 对业务源码 `05f7af7` 的[应用构建与部署验证](https://github.com/480305373-dotcom/smart-ticket-system/actions/runs/39374711421)和[分布式完整验收](https://github.com/480305373-dotcom/smart-ticket-system/actions/runs/39374711365)均已通过。后者覆盖压测、双库恢复、正式 HTTPS、浏览器流程以及 Kubernetes 故障自愈。云端正式部署未启用 AI；真实 RAG 出处验证来自上述本机报告。

## 成品包

```powershell
# 验证代码、真实基础设施测试和前端构建
pwsh ./scripts/verify-product.ps1 -LocalInfrastructure

# 六份验收报告均通过，且真实 RAG 已验证后才能封装稳定成品
python scripts/package_release.py --product
```

本机校验默认复用已经安装的前端依赖；需要按锁文件重新安装时使用 `-InstallDependencies`，Windows 上先停止本项目 Vite 开发服务，避免运行中的 esbuild 文件被锁定。

输出为 `artifacts/smart-ticket-system-complete.zip` 和配套 `.sha256`。ZIP 包含源码、四个可执行 JAR、前端 dist、SQL、部署脚本、报告与指南；根目录 `RELEASE-MANIFEST.json` 提供逐文件 SHA-256 和六份验收证据摘要。排除本机密码、JWT、备份、证书私钥、日志、个人简历和临时文档。

全新电脑启动步骤见 [正式部署指南](FORMAL_DEPLOYMENT.md)。默认启动需要联网获取容器基础镜像；`-SkipImageBuild` 只适用于已有本机镜像的机器。

## 使用与学习边界

当前成品具备完整业务、分布式组件、可重复部署和可核验交付流程。PostgreSQL 与 Eureka 仍是单实例，本机多容器不能代替跨物理机容灾；未宣称生产 SLA、生产并发上限或公网部署。浏览器信任本机 CA 需要用户自行处理，脚本不修改系统信任库。

成品固定后，自己重新实现的模块顺序见 [重写路线](REBUILD_GUIDE.md)。
