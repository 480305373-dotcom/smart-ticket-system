# 分布式能力交付与验收

日期：2026-10-20。保留已有项目、业务、单体入口与数据库卷，在原代码基础上新增分布式模式。知识领域与核心工单领域分别部署、拥有不同数据库账号；没有重建原项目。

## 已补齐的四项能力

| 原边界 | 新增实现 | 已完成的验证 |
|---|---|---|
| 仅单体业务应用 | 核心服务、独立知识服务、Gateway、Eureka；两个核心实例与两个知识实例 | 请求实际分布到两个实例；知识正文只在所属库，核心数据库账号不能连接知识库；停止知识实例后仍可访问 |
| Redis 单节点 | 3 主 3 从 Redis Cluster、AOF、密码、拓扑刷新 | 16384 槽覆盖；停止真实主节点，副本提升后持续访问成功；节点恢复后验证幂等写入，再进行下一项故障 |
| RabbitMQ 单节点 | 3 节点集群、主审计与死信 Quorum 队列、目标 3 副本、多地址连接 | 检查实际队列成员；停止审计队列 leader 后仍能创建工单并产生异步审计；恢复 3 副本在线 |
| 无 Kubernetes | 三节点 kind，Deployment、StatefulSet、PVC、Service、Secret/ConfigMap、探针、资源限制、PDB、滚动更新 | GitHub Actions 真集群完成 14 项资源及恢复检查、23 项 HTTP 检查；知识 Pod 自动替换；滚动更新的 15 次 HTTP 采样全部成功 |

## 构建和业务回归

| 验收 | 结果 | 证据 |
|---|---|---|
| 后端构建及真实中间件测试 | 43 项通过，0 失败、错误、跳过；含知识服务的真实 PostgreSQL 启动测试与注册/退出生命周期测试 | [本机构建记录](distributed-build-verification.json)；[之前的完整 CI](https://github.com/480305373-dotcom/smart-ticket-system/actions/runs/39266307672) |
| 前端 | 3 项 Node 测试通过，Vite 构建成功 | 本机与 CI |
| 分布式业务、数据库边界与故障回归 | 本机 38 项通过 | [本机记录](distributed-verification.json) |
| 原有业务接口经新网关回归 | 69 项通过 | [业务接口记录](distributed-api-verification.json) |
| 真实依赖与 AI 问答 | 12 项通过，回答含知识出处 | [完整模式记录](distributed-enterprise-verification.json)、[实际模型回答](distributed-ai-verification.json) |
| Redis 独立持续停机检查 | 原主节点仍关闭时，最后 10 次 HTTP 采样连续成功；初期有预期恢复窗口 | [采样记录](redis-failover-verification.json) |
| 云端 Compose 集群 | 已通过业务与节点故障检查 | [云端记录](ci-distributed-verification.json) |
| 云端 Kubernetes | 14 项基础设施/自愈检查、23 项 HTTP 检查通过 | [资源与恢复](kubernetes-verification.json)、[HTTP](kubernetes-http-verification.json) |

真实三节点 Kubernetes 验收：[Verify distributed architecture 39266307679](https://github.com/480305373-dotcom/smart-ticket-system/actions/runs/39266307679)。报告保存了对应源码提交与运行链接。报告中的检查数量是验收项数量，不是覆盖率、吞吐量或生产收益。

本机 Docker Hub 下载节点镜像时持续发生 EOF，未在本机启动 Kubernetes。Kubernetes 的已完成结果来自 GitHub Actions 内实际创建并部署的 kind 集群，不能误称为本机已部署。核心、知识、Eureka、Redis Cluster、RabbitMQ Quorum 和 AI 则已在本机真实运行验证。

## 运行入口

- 本机分布式成品：`http://127.0.0.1:8090`。
- Eureka 管理入口：`http://127.0.0.1:8501`，仅绑定本机回环地址。
- 原单体开发入口：`http://127.0.0.1:5173`，继续保留。
- 原独立容器成品 `8088` 与原数据库卷没有被替换。

演示用户 dev_zhangsan，演示管理员 dev_admin1，演示密码均为 Demo@12345，仅用于开发演示模式。

```powershell
.\scripts\start-distributed.ps1
# 已有构建产物与镜像时
.\scripts\start-distributed.ps1 -SkipBuild -SkipImageBuild
```

启动、停止、秘密配置、Kubernetes 与故障验证详见 [分布式指南](DISTRIBUTED_GUIDE.md)。新拓扑使用隔离的新数据，不自动迁移或覆盖原业务数据；测试仅清理自己创建的临时资源，保留带验证标记的工单作为运行证据。

## 保留的真实边界

PostgreSQL 和 Eureka 当前仍为单实例。三个 Kubernetes 节点运行在同一台 CI runner，未做跨物理机或跨可用区故障演练。没有新增生产容量数字、云端域名/TLS、服务网格或通用跨服务事务。默认分布式配置带演示账号，不应直接作为公网生产配置使用。
