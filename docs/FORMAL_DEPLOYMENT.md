# 独立正式环境与 HTTPS

正式环境使用 `smartticket-formal` Compose 项目、独立数据卷、核心/知识两个数据库和随机密码，不导入开发演示账号。后端运行 `prod,distributed`，可额外启用 `ai`。注册默认关闭，管理员通过私密配置初始化，正常 API 限流为每用户每分钟 120 次。

## 环境要求

- Docker 与支持 `!override` 的 Docker Compose；建议为 Docker 准备至少 8 GiB 内存，避免同时运行多套集群。
- PowerShell 7.5 或更新版本，用于跨平台脚本和完整的本机证书生成。
- 从源码构建还需要 Java 21、Maven、Node.js 22；成品 ZIP 已附可执行 JAR 和前端 dist，可跳过源码构建。
- 开启 AI 时，宿主机 Ollama 需要事先准备 `qwen3:4b` 和 `nomic-embed-text`。模型不随成品 ZIP 分发。

## 启动

从成品 ZIP 解压后的根目录执行：

```powershell
# 复用发布包内的 JAR / dist，构建容器镜像并启动正式环境
pwsh ./scripts/deploy-formal.ps1 -SkipBuild

# 已有本机模型时启用真实 RAG 问答
pwsh ./scripts/deploy-formal.ps1 -SkipBuild -WithAi

# 只有本机已存在相应 smartticket 镜像时才可跳过镜像构建
pwsh ./scripts/deploy-formal.ps1 -SkipBuild -SkipImageBuild -WithAi
```

首次启动会创建 `.runtime/formal.env`，其中 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 是初始管理员凭据。密码不显示在公开输出中，不随源码或发布包分发。请在自己电脑的编辑器中打开该文件查看；不要把它提交到 Git。

打开 `https://localhost:8443`。`http://localhost:8089` 会跳转到 HTTPS。数据库、Redis、RabbitMQ 和 Eureka 没有宿主机端口映射。这里只绑定本机回环地址，没有将服务发布到互联网。

如果旧 8090 演示环境占用内存，可单独停止它，保留全部数据卷：

```powershell
docker compose -p smartticket-cluster --env-file .runtime/distributed.env -f compose.distributed.yaml stop
```

## 本机证书

脚本生成仅用于本机的 CA 与含 localhost、127.0.0.1、IPv6 回环 SAN 的服务证书。客户端验收使用 `.runtime/formal/tls/ca.crt` 校验证书链和域名，未关闭证书校验。证书包含严格校验要求的密钥标识与用途信息。

脚本不会修改系统或浏览器信任库。浏览器首次访问会提示本机 CA 不受信任；如需去掉提示，可自行将 `ca.crt` 导入当前用户的受信任根证书库，导入前确认文件来自自己的本次部署。浏览器 UI 自动化仅为测试页面流程设置独立上下文的证书例外，HTTPS 安全验收由另一个要求 CA/域名校验的程序完成。

证书临近到期时显式更新，然后重新加载前端：

```powershell
pwsh ./scripts/new-local-tls.ps1 -Renew
docker compose -p smartticket-formal --env-file .runtime/formal.env -f compose.distributed.yaml -f compose.formal.yaml exec -T frontend nginx -s reload
```

续期会生成新的本机 CA；如果此前手动导入过旧 CA，需要自行更新信任。私钥只保存在被忽略的 `.runtime/formal/tls/server.key`，不进入发布包。

## 验收、备份与停止

```powershell
# 证书、正式配置、安全边界、完整工单/知识流程
python scripts/formal_smoke.py

# 同时要求真实 AI 回答和已发布知识出处
python scripts/formal_smoke.py --require-ai

# 正式双库备份与独立恢复演练，需要短暂维护窗口
pwsh ./scripts/verify-restore.ps1 -Formal

# 停止但保留数据
docker compose -p smartticket-formal --env-file .runtime/formal.env -f compose.distributed.yaml -f compose.formal.yaml stop
```

验收会创建临时业务资源，结束后删除测试工单及文章、停用验收账号并保留审计记录。首次启动的正式库不包含开发账号和演示工单，管理员可以从“用户与权限”创建自己的普通账号。

后续启动请保留原 `.runtime/formal.env`、证书与数据卷。如果数据卷存在但凭据文件丢失，脚本会拒绝生成另一套密码。没有自动迁移或覆盖旧演示库。

## 范围

这是已经在本机验收的正式配置与交付流程，不是公网托管服务。PostgreSQL 与 Eureka 仍是单实例，多个容器运行在同一台机器；没有宣称跨机高可用、生产 SLA 或生产容量。公网部署时应改为自己的域名、受信任的服务器证书、网络入口策略与备份设施，并重新执行环境验收。
