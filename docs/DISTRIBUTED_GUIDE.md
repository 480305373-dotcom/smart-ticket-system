# 微服务、Redis Cluster、RabbitMQ Quorum 与 Kubernetes

本次是在已有项目上增加分布式运行模式。原来的单体开发方式、业务接口、数据卷和运行入口保留。新的 Compose 项目名称为 `smartticket-cluster`，Kubernetes 命名空间为 `smartticket`，不复用原业务数据库卷。

## 1. 现在的服务边界

```text
浏览器 → Nginx → Spring Cloud Gateway
                   ├─ lb://smartticket-core       （2 实例）
                   │    ├─ 账号 / JWT / 工单 / 评论 / 历史 / 审计
                   │    ├─ Outbox / Inbox
                   │    └─ AI 问答与向量索引，调用知识服务取得当前正文
                   └─ lb://smartticket-knowledge  （2 实例）
                        └─ 知识文章 / 发布 / 搜索 / 关键词推荐

Eureka：实例注册、心跳、服务发现
PostgreSQL：intelligent_ticket 与 smartticket_knowledge 两个数据库
Redis Cluster：3 主 3 从，16384 槽
RabbitMQ：3 节点，审计主队列与死信队列均采用 3 副本 Quorum
```

这次拆分具有独立业务入口和数据库所有权。并非把同一个完整后端启动两份就称作微服务。工单与账号目前仍在核心服务内，尚未进一步拆成用户服务、工单服务和审计服务。

知识服务复用原来已测试的知识领域代码，构建时依赖 backend 的 library 分类 JAR；它只导入知识相关组件，不扫描核心服务全部 Bean。独立可执行 JAR 不启动工单 Controller，不访问核心业务表。后续可以继续将复用代码提取为更小的领域库，当前没有宣称所有服务的构建依赖完全解耦。

## 2. 注册发现与网关

使用 Spring Cloud 2025.1.3，与当前 Spring Boot 4.1.1 配套。Gateway 的公开路径按业务路由：`/api/knowledge/**` 到知识服务，其余工单、账号、AI、管理接口到核心服务。目标使用 `lb://`，内部知识调用使用带 LoadBalancer 的 RestClient，不写死实例 IP。

实例使用唯一 ID、心跳和注册信息更新；调用有连接/读取超时。网关不公开 `/internal/**`，并移除客户端伪造的服务密钥和身份头。业务服务仍执行后端认证及资源权限检查。

核心与知识实例只在 readiness 通过后向 Eureka 报告 UP；可选的 RabbitMQ 短暂不可用不触发服务摘除，审计由 Outbox 重试。正常退出时先将客户端健康状态设为 DOWN，并保持 HTTP 服务 20 秒，覆盖注册缓存、客户端拉取与负载均衡缓存的传播时间，再执行 HTTP 优雅关闭。使用可自动恢复的 DOWN，避免把运维摘除状态 OUT_OF_SERVICE 用作临时健康状态。退出期间的健康更新不会把实例重新标为 UP。Compose 与 Kubernetes 给这两个服务保留 60 秒退出时间，滚动更新验收仍要求每次 HTTP 采样成功。

响应提供 `X-SmartTicket-Service` 和 `X-SmartTicket-Instance`，用于验收实际请求落在哪个服务/实例，不携带敏感信息。

单体默认关闭 Eureka 客户端；只有 distributed 配置启用。注册中心默认单实例且只向本机回环地址展示管理入口，本地验收关闭自我保护以便观察实例移除。它不是多节点注册中心高可用承诺。

## 3. 拆分后如何保持账号安全

知识服务不复制一份用户密码或读取 app_user。收到公开请求时，它携带原 Bearer Token 和内部服务凭据调用核心 `/internal/identity`。核心仍验证 JWT 签名、到期、持久化撤销、enabled、角色和 auth_version。

因此退出、改密、停用、角色变化后的旧 Token，在两个知识实例上仍受同一规则约束。不会因为只验 JWT 签名而丢掉原来的即时撤销能力。该核验也经过共享 API 限流；429 会传回调用者。

核心调用知识内部接口需要随机生成的服务凭据；内部接口只在私有网络使用，网关不转发。凭据保存在 `.runtime/distributed.env` 或 Kubernetes Secret，不进入源码和发布包。

当前核验依赖核心服务可用性，失败明确返回 503，没有悄悄放行。没有采用微服务内任意 Header 即可信的方案，也没有实现 OAuth2 授权服务器或服务网格 mTLS。

## 4. 数据库所有权与一致性

同一 PostgreSQL 实例内创建两套数据库和账号：

| 服务 | 数据库 | 登录角色 | 访问范围 |
|---|---|---|---|
| 核心 | intelligent_ticket | core | 用户、工单、历史、审计、Outbox/Inbox、向量等 |
| 知识 | smartticket_knowledge | knowledge | 知识正文、发布状态、分类与关键词 |

数据库 CONNECT 权限分别授予所属角色，核心账号不能连接知识库。物理数据库实例仍是单个 PostgreSQL，没有宣称 PostgreSQL 高可用。

知识 created_by 是外部用户 ID，不做跨数据库外键。工作台文章数量、工单知识推荐及 AI 的当前正文/发布状态都通过知识服务取得，不以核心库残留的旧文章表为权威数据源。分布式模式使用独立的 knowledge_external_vector_index，不对外部文章 ID 建本地外键。

核心域内原来的事务、状态条件更新和请求幂等保留。跨服务调用采用明确失败和超时，未引入跨库原子事务或通用 Saga。演示初始化使用新隔离数据，原业务库没有自动迁移或覆盖；如将既有生产数据切换为此拓扑，需要另行执行受控的数据迁移与校验。

向量索引同步使用 PostgreSQL session advisory lock，两个核心实例不会同时删除和重建同一份索引；锁在专用连接中持有，异常也释放。该协调不把向量操作变成跨系统原子事务。

## 5. Redis Cluster

六个 Redis 7 节点启用 cluster 模式与 AOF，分为三个主节点及三个副本。初始化脚本检查现有集群，不自动清空槽位或重置已有节点；部分初始化失败时停止并要求排查，避免重建覆盖。

核心配置多个种子地址、最大重定向次数、Lettuce 自适应与定时拓扑刷新。缓存、登录失败计数、限流和撤销标记复用原实现，Lua 限流只操作一个 key，不依赖跨槽原子操作。

节点间和客户端都使用密码，Redis 端口不映射到宿主机。缓存继续按权限隔离，清理用 SCAN；撤销仍以 PostgreSQL 为持久依据。

验证需要看到 `cluster_state:ok`、16384 个已分配槽、3 主 3 从，并实际停掉主节点，观察副本提升及应用恢复。Redis 异步复制存在持久性边界，不代表任何故障下都不丢缓存或计数。

## 6. RabbitMQ 集群与 Quorum 队列

三个 RabbitMQ 4 节点使用相同 Erlang cookie 和确定的节点名进行集群发现。客户端配置三个地址，不只连单节点。分布式模式声明主审计队列与死信队列为 Quorum，初始目标组大小为 3；普通单体仍保持原队列类型，避免对已有 classic 队列声明冲突。

配置持续成员协调，目标 3 副本，处理初次启动时节点尚未全部加入的情况；不会自动移除暂时离线的成员。主队列采用 at-least-once 死信策略和 reject-publish 溢出策略，配合原有有限消费重试、Outbox、Confirm/Return、Inbox 事务和死信恢复。

验收不只检查“有三个进程”，还要检查队列的 type、members 和 online，并停止审计队列实际 leader，验证另一节点选主后仍能产生审计。三副本的多数派为 2；同时失去两个成员仍可能不可用。

所有节点持久卷独立，AMQP、管理与集群通信端口仅在项目网络内使用。

## 7. 本地 Compose 运行

需要 Java 21、Node、Maven、Python 3、PowerShell 和 Docker Desktop。完整拓扑包含多个 Java 实例和中间件，Docker 至少准备约 8 GiB 内存；同时保留多套演示环境时需额外余量。

```powershell
# 编译并启动，自动产生仅本机保存的随机凭据
.\scripts\start-distributed.ps1

# 已构建过时重用构建结果
.\scripts\start-distributed.ps1 -SkipBuild

# 镜像已就绪时不再访问基础镜像仓库
.\scripts\start-distributed.ps1 -SkipBuild -SkipImageBuild

# 实际业务、权限、数据库边界与故障验证
python scripts/distributed_smoke.py --faults
```

入口 `http://127.0.0.1:8090`，注册中心 `http://127.0.0.1:8501`。演示用户 dev_zhangsan、dev_lisi，管理员 dev_admin1，演示密码 Demo@12345。此默认模式会初始化模拟企业数据，不作为直接暴露互联网的正式部署配置。

保留并备份 `.runtime/distributed.env`。已有卷而凭据丢失时，脚本会停止，要求恢复原凭据；不会重新生成密码、删除卷或重置数据。

脚本使用独立 Compose 项目。停止时保留数据卷：

```powershell
docker compose -p smartticket-cluster --env-file .runtime/distributed.env -f compose.distributed.yaml stop
```

不需要运行 down -v，也不需要停止原来的 PostgreSQL 5432、演示库 5433 或 8088 成品部署。原 5173 单体开发入口继续可用。

配置文件为可审阅的 JSON 格式 YAML，由 `scripts/generate-cluster-compose.py` 确定性生成。修改拓扑后重新生成并执行 `docker compose ... config --quiet`，不要把展开了秘密的完整配置输出提交到仓库。

## 8. Kubernetes 资源

`deploy/kubernetes/resources.json` 与 kustomization 包含：

- 专用 Namespace；应用配置 ConfigMap；凭据从运行时创建的 Secret 引用。
- 核心、知识、Gateway、前端的双副本 Deployment；Eureka Deployment。
- Redis 6 副本、RabbitMQ 3 副本和 PostgreSQL 的 StatefulSet 与独立 PVC。
- Headless Service 提供稳定的节点 DNS；ClusterIP 连接应用；本机 NodePort 入口。
- Redis 集群初始化 Job、核心启动前的依赖等待。
- startup、readiness、liveness 探针，优雅关闭、资源请求与内存限制。
- 滚动更新 maxUnavailable=0；PodDisruptionBudget。
- RabbitMQ 三个 Pod 强制分散到三个 Kubernetes 节点；其余组件配置拓扑分布。

没有使用 ConfigMap 存密码。默认不挂载 Kubernetes API Token，也不授予应用集群管理权限。Redis 主从对在节点层面的精确安置并非仅靠普通拓扑分布就能保证，生产环境仍需结合节点与故障域策略规划。

本地验证使用 kind 三节点，创建独立 `.runtime/kubeconfig`，不修改原来的用户 kubectl 上下文。先按 kind 官方文档安装/验证工具，再执行：

```powershell
# 建议先停止本次 Compose 拓扑，释放内存；保留其卷
.\scripts\deploy-kubernetes.ps1 -KindPath .runtime/tools/kind.exe
python scripts/kubernetes_smoke.py
```

入口 `http://127.0.0.1:8091`。脚本预载已构建应用和中间件镜像，创建 Secret 并应用清单。后续查询需要显式指定 kubeconfig：

```powershell
kubectl --kubeconfig .runtime/kubeconfig -n smartticket get pods,pvc
```

`kubernetes_smoke.py` 校验专属上下文，等待资源就绪，验证数据库卷、Redis 槽、Quorum 副本及整个 HTTP 流程；再删除一个知识 Pod 检查自动替换，并在滚动更新期间持续读取知识接口。

本机三个 Kubernetes 节点仍运行在同一个 Docker 宿主机上，不能据此声称通过跨物理机、跨可用区或宿主机断电演练。

## 9. 验证与 CI 的证据标准

后端增加 DistributedBoundaryTests，覆盖远程分页/时间转换、远程失败不回读本地表、草稿不可见、内部服务凭据、Quorum 声明及索引锁跳过/异常释放。

```powershell
mvn -B package '-Dinfra.tests=true'
.\scripts\npm.ps1 --prefix frontend test
.\scripts\npm.ps1 --prefix frontend run build
```

`.github/workflows/clusters.yml` 在独立 Linux runner 执行 Compose 业务与节点故障验证，再停止其容器以释放内存，在真实 kind 集群部署同一组镜像，验证资源和自愈。原 CI 的单体、正式部署和监控验收继续保留。

运行结果分别写 `docs/distributed-verification.json`、`docs/kubernetes-http-verification.json` 与 `docs/kubernetes-verification.json`；失败细节仅写本地 `.runtime` 中指定的脱敏报告。**只有对应报告实际为 PASS，才可把该环境列为已经验收。**

本轮完成情况及本机/云端证据见 [分布式交付报告](DISTRIBUTED_DELIVERY.md)。节点选主和客户端拓扑刷新可能短暂返回 503；验收会在原节点仍停机时继续采样，并等待节点与幂等写入完全恢复，再注入下一种故障。没有把连续故障交叠造成的恢复窗口伪装成即时无中断。

## 10. 面试时可准确介绍的变化

“项目保留了便于学习和启动的单体模式，另外按业务边界拆出知识微服务。分布式模式通过 Spring Cloud Gateway 和 Eureka 进行注册发现与负载均衡，知识服务有独立数据库及账号，核心通过服务接口取得知识内容。Redis 使用 3 主 3 从 Cluster，RabbitMQ 使用 3 节点及 Quorum 队列。部署提供 Kubernetes 的多副本应用、StatefulSet、PVC、探针、滚动更新和故障验证。具体已经在哪些环境通过，以验收报告为准。”

仍需保留的边界：单 PostgreSQL 与单 Eureka、未做跨可用区演练、未建立容量压测数字、未配置云端域名和 TLS、没有通用跨服务事务、没有服务网格或多租户体系。新增四项能力不意味着已经解决所有生产高可用和安全治理问题。

## 11. 官方依据

- [Spring Cloud 与 Spring Boot 版本对应](https://spring.io/projects/spring-cloud/)
- [Eureka 注册发现](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html)
- [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Redis Cluster 规范](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/)
- [RabbitMQ Quorum 与成员协调](https://www.rabbitmq.com/docs/quorum-queues)
- [kind 安装和本地集群](https://kind.sigs.k8s.io/docs/user/quick-start/)
