# 开发完成与验收报告

> 本文保留 2026-10-03 基础版验收历史。2026-10-18 完整版新增 Redis、可靠消息、账号管理、AI 与部署能力，当前运行与验证以 [完整交付报告](FINAL_DELIVERY.md) 和 [完整版指南](ENTERPRISE_GUIDE.md) 为准。

验收日期：2026-10-03。开发依据：根目录 PROJECT_TASK.md。已在原有项目中实际增量开发并进行 PostgreSQL、后端、前端和浏览器验证。

## 1. 项目目录结构

保留 backend Spring Boot 项目，新增根 pom.xml 聚合入口、frontend Vue 项目、sql 安全结构脚本、scripts 启动/验证脚本和 docs 资料。后端新增 KnowledgeController、TicketActivityController、ArticleService、TicketActivityService、评论/历史/文章实体及 Mapper、DataInitializer、dev 配置；前端入口、Axios、Pinia、路由和全部业务页面已落地。完整目录在 README 的“架构与目录”。

## 2. 已有与新增功能

原有注册登录、BCrypt、JWT、Spring Security、工单 CRUD/分页、USER 数据隔离、管理员权限、审计和扩展模块保留。

新增中文企业工作台、角色导航、Dashboard、筛选、编辑删除界面、评论、自动历史、知识文章 CRUD/草稿/发布、标题分类关键词查询、关键词推荐、幂等开发数据。

继续完善工单处理人指派/取消、处理人队列与筛选、指派历史、带 X-Request-Id 的创建幂等；批量关联用户名、单 SQL Dashboard 聚合、推荐正文按命中加载及 Element Plus 按需加载。演示和面试讲解已补充到 docs/DEMO_GUIDE.md。

修复普通 PUT 绕过状态权限、可伪造创建状态与时间、更新时间未刷新、并发状态覆盖风险、注册重名错误处理、每次登录 Token 唯一性。旧 testuser 的未编码字段已转换为同一值的 BCrypt 哈希，已有 BCrypt 账号没有重置。

## 3. 数据库

| 数据 | 最终数量 |
|---|---:|
| 用户 | 9 |
| BCrypt 用户 | 9 |
| 工单 | 34（原有 9 + 新增演示 25） |
| 知识文章 | 12 |
| 工单评论 | 26 |
| 处理历史 | 49 |
| 初始化标记 | 1 |

核心表 app_user、ticket、ticket_comment、ticket_history、knowledge_article，辅以 operation_log、token_blacklist、demo_seed、ticket_request；原 vector_store 保留。工单关联创建人和处理人，评论和历史关联工单及操作人，文章关联作者。ticket_request 以用户和请求编号唯一键记录摘要与结果；删除工单时对应结果外键置空，原请求不能重新创建。评论/历史随对应工单级联删除，审计保留。详细外键与索引见 sql/schema.sql。

没有 DROP 数据库/表，没有清空已有数据。多次启动、测试与重启后仍为 25 条演示工单、12 篇文章及一个初始化标记，确认没有重复初始化。

## 4. 主要 API

| Method | URL | 权限 | 作用 |
|---|---|---|---|
| POST | /users/register、/users/login | 公开 | 注册 / 登录 |
| POST | /users/logout | 登录 | 黑名单撤销当前 JWT |
| GET | /tickets/page、/tickets/statistics | USER 本人 / ADMIN 全部 | 条件分页、Dashboard |
| POST | /tickets | 登录 | 创建本人工单 |
| GET、PUT、DELETE | /tickets/{id} | 本人 / ADMIN | 详情、编辑、删除 |
| PATCH | /tickets/{id}/status | ADMIN | 状态流转、自动历史 |
| PATCH | /tickets/{id}/assignee | ADMIN | 指派/取消处理人、自动历史 |
| GET | /users/handlers | ADMIN | 仅 ID/用户名的可指派管理员列表 |
| GET、POST | /tickets/{id}/comments | 本人 / ADMIN | 查询、新增留言 |
| GET | /tickets/{id}/history | 本人 / ADMIN | 处理历史 |
| GET | /tickets/{id}/knowledge-recommendations | 本人 / ADMIN | 已发布知识推荐 |
| GET | /knowledge、/knowledge/{id}、/knowledge/categories | 登录 | 列表/详情/分类，USER 不可见草稿 |
| POST、PUT、DELETE | /knowledge、/knowledge/{id} | ADMIN | 新增、修改发布、删除 |

完整参数与旧接口兼容说明见 README 的“API 清单”。成功统一 code/message/data；异常使用正确 HTTP 状态码，不向前端输出堆栈。

## 5. 前端页面

| URL | 页面 | 权限 |
|---|---|---|
| /login | 登录 | 未登录 |
| /dashboard | 当前用户、角色、数据库统计及状态图 | 登录 |
| /tickets、/tickets/create | 列表与创建 | 登录；列表按角色隔离 |
| /tickets/:id、/tickets/:id/edit | 详情/编辑、评论历史推荐 | 本人 / ADMIN |
| /knowledge、/knowledge/:id | 知识列表与详情 | 登录；USER 仅已发布 |
| /admin/tickets | 全局工单、用户名筛选与详情 | ADMIN |
| /admin/assigned | 分配给当前管理员的队列 | ADMIN |
| /admin/knowledge | 知识管理 | ADMIN |
| /admin/knowledge/create、/admin/knowledge/:id/edit | 新增、编辑与发布 | ADMIN |

所有页面实际对接 API，没有 Vue 假业务数据。桌面布局含左侧菜单、顶部用户角色和退出、内容卡片；768px 响应式页面已验证无整体横向溢出。

## 6. 开发测试账号

| 账号 | 角色 | 开发密码 |
|---|---|---|
| dev_zhangsan | USER | Demo@12345 |
| dev_lisi | USER | Demo@12345 |
| dev_wangwu | USER | Demo@12345 |
| dev_admin1 | ADMIN | Demo@12345 |

数据库中保存 BCrypt。原有 zhangsan/admin1 密码不重置，不假定它们等于演示密码。

## 7. 如何运行

1. 保持 PostgreSQL 运行，数据库 intelligent_ticket 可访问。当前本机使用 localhost:5432。
2. 配置 DB_PASSWORD 与数据源参数，或使用被 Git 忽略的 backend/config/application-local.properties。原有本机密码已迁入此文件，没有出现在新的 README、前端或 SQL 中。
3. 根目录运行 `.\scripts\start-backend.ps1`；等价命令：进入 backend，执行 `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'`。
4. 另一个终端运行 `.\scripts\start-frontend.ps1`；标准命令：进入 frontend，`npm install`、`npm run dev`。
5. 浏览器打开 http://127.0.0.1:5173，后端为 http://localhost:8080。

当前前后端已在本机启动，可直接登录。dev 只要求 PostgreSQL；原 Redis/MQ/AI 扩展使用非 dev 配置与原有基础设施。

## 8. 实际验证结果

| 验证 | 结果 |
|---|---|
| 初始后端 mvnw.cmd test | 通过，确认原始项目可编译且数据库可连接 |
| 最终 mvn clean package | BUILD SUCCESS，20 个 JUnit 测试，0 失败/错误/跳过 |
| npm install | 成功；audit 0 vulnerabilities |
| 最终 npm run build | 成功，产物位于 frontend/dist |
| 真实 HTTP | 69 个核心检查 + 3 个清理请求，均通过 |
| Chromium 浏览器 | 使用生产 dist 预览验证 17 个流程，页面脚本错误为 0 |
| PostgreSQL 检查 | 真数据、BCrypt、优先级/状态覆盖、幂等标记全部通过 |

HTTP 验证包括未登录/非法 JWT、错误密码、参数边界、工单创建读取更新删除、跨用户详情/评论/历史/推荐/更新/删除 403、USER 状态修改 403、管理员非法流转 400、完整合法流转及三条历史、真实分页筛选与统计、管理员留言、草稿权限、文章编辑发布、知识查询与分类、退出后 Token 401。

JUnit 包含 12 个真实数据库业务测试、2 个独立事务并发测试、1 个历史失败回滚测试、4 个 JWT 过滤器测试以及 1 个启动测试。新增验证指派权限与历史、同请求重复创建和内容冲突、并发处理只写一次状态历史、并发同请求只创建一条工单、历史写入失败时工单与请求认领一起回滚、过期 JWT 不读数据库、撤销存储异常返回不含连接细节的 503、业务异常不会误转为 JWT 401。

普通数据库业务测试事务回滚自己的数据；独立事务测试和 HTTP/浏览器测试清理自己创建的工单/文章，不删除既有记录。正常审计、退出黑名单、HTTP 幂等请求记录保留。

可重复验证脚本：scripts/api_smoke.py、scripts/ui_smoke.cjs、scripts/db_verify.py。请求与检查清单存于 api-verification.json、ui-verification.json、database-verification.json。screenshots 保存登录、工作概览、工单详情、知识库、平板视图；截图代表实际测试页面，部分会包含当时尚未清理的测试资源。

## 9. 尚未完成或限制

PROJECT_TASK.md 要求的本机核心演示流程已完成，没有数据库密码、权限或构建阻塞。

- 本次没有完整验证保留的 Redis、RabbitMQ、Ollama、pgvector 扩展端到端流程；dev 明确关闭外部服务依赖。
- 推荐是基础关键词匹配，不具备大模型或语义检索能力。
- Element Plus 已改为组件和样式按需加载，最终构建没有大于 500kB 的分块提示。仅按构建产物核验，未声称运行吞吐量提升。
- 没有执行公网部署；静态生产部署需要 README 中说明的反向代理、路由回退和环境配置。

## 10. 给初学者的代码导读

建议阅读顺序：SQL → Entity → Mapper → TicketService → Activity/ArticleService → Controller/DTO → 异常处理 → UserService → JWT/Filter/Security → dev 初始化与测试 → 前端 Pinia/Axios/Router → 登录 → 工单列表/表单/详情 → Dashboard → 知识页面。

带着一个具体流程阅读：创建工单如何取得 JWT 身份，如何写 ticket 和 CREATE 历史；管理员 PATCH 如何验证权限、校验前后状态、在事务中写 history；用户 A 访问用户 B 的工单为何从后端返回 403；文章从草稿变为已发布后为何能被 USER 查询；前端如何展示这些真实响应。README 最后一节给出了对应文件与逐步阅读目标。
