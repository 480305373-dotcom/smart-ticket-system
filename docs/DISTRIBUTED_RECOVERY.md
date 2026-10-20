# 分布式双库备份与恢复演练

核心数据库 `intelligent_ticket` 与知识数据库 `smartticket_knowledge` 分属不同应用账号。旧版单库备份不能覆盖分布式版的知识正文，分布式环境请使用下面的专用入口。

```powershell
# 生成双库备份，保存在 .runtime/backups/distributed/ 下
.\scripts\backup-db.ps1 -Distributed

# 完整执行一次备份及隔离恢复演练
.\scripts\verify-restore.ps1 -Distributed

# 也可以验证某一份已经存在的备份
python scripts/distributed_backup.py verify --backup .runtime/backups/distributed/具体备份目录

# 独立正式环境使用自己的双库与私密凭据
.\scripts\verify-restore.ps1 -Formal
```

## 一致性和业务中断

不同数据库的独立 `pg_dump` 不能自动形成跨库原子快照。本工具在备份前停止 `smartticket-cluster` 中原本运行的核心与知识服务，等待优雅退出，然后对两个库分别采集内容摘要、序列状态和归档。

整个过程属于维护窗口，8090 入口可能暂时不可用。已知应用写入方都停下后才开始取备份；如果另有外部程序直接写数据库，需要同时纳入维护窗口。不会停止或修改原开发环境、8088 成品环境或这些环境的数据卷。

不论备份成功与否，工具都会尝试重启原本运行的每个应用实例，并等待健康检查通过。被强制杀死的进程无法执行清理，此时手动运行 `start-distributed.ps1 -SkipBuild -SkipImageBuild` 恢复应用。

## 恢复验证

1. 校验两个归档的 SHA-256，缺库、文件损坏或未知扩展立即拒绝恢复。
2. 创建独立临时 PostgreSQL 容器，使用私有临时密码、无网络、无端口和内存文件系统。
3. 重建核心与知识两个应用账号及数据库连接权限；pgvector 扩展由管理员安装，业务表恢复为各自应用账号所有。支持框架实际使用的 plpgsql、vector、hstore 和 uuid-ossp 扩展，未知扩展会拒绝恢复。
4. 在两个空库中分别执行 `pg_restore --exit-on-error`。
5. 比较所有 public 业务表的行数、排序后的行内容摘要及全部序列状态，验证应用账号能够查询恢复后的表。
6. 验证核心账号不能连接知识库，知识账号不能连接核心库。
7. 只清理本次创建且标签匹配的临时容器，不向源数据库恢复任何数据。

内容摘要用于核对逻辑恢复结果，归档 SHA-256 用于检验文件完整性。验证会扫描全部表，当前适用于本项目演示规模；大数据量部署需要安排扫描时间与临时容器空间。默认临时存储上限为 512 MiB。

备份内含真实账号哈希、业务正文和审计数据，始终留在被 Git 忽略的 `.runtime/`。公开报告 `docs/distributed-restore-verification.json` 仅包含表数量、总行数、校验结论和本次演练耗时，不包含正文、密码或归档文件。

本演练证明逻辑备份能够恢复，不等于 PostgreSQL 主从故障切换，也不代表已经承诺生产 RTO/RPO。JWT 签名密钥、服务密钥及应用数据库密码仍需单独私密保管，不能由业务归档重新生成。

该归档仅覆盖两个 PostgreSQL 数据库。RabbitMQ 持久卷、部署私密配置与证书需要单独保留；更换为全新消息集群时，还需要核对已发送但尚未消费的事件，不能把本演练表述为整个消息系统的灾难恢复保证。
