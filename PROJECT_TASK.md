你现在正在接手一个已经开发了一部分的 Java Spring Boot 项目：

# 项目名称

企业智能工单与知识协同平台  
Smart Ticket System

你的任务不是重新创建项目，而是在现有代码基础上继续开发，把它完善成一个：

- 可以启动
- 可以登录
- 有完整前端界面
- 有 PostgreSQL 数据库
- 有较丰富测试业务数据
- 普通用户和管理员权限不同
- 可以完整演示工单生命周期
- 包含基础知识库功能
- 适合作为 Java 后端 / 软件开发岗位简历项目

的完整 Web 项目。

---

# 一、第一原则：先阅读现有项目，不允许推倒重写

首先完整检查当前仓库，包括：

- pom.xml
- application.yml / application.properties
- entity
- mapper
- service
- controller
- security
- JWT
- exception
- PostgreSQL 配置
- 当前 SQL
- README
- Git 状态

先搞清楚已经实现了什么。

不要重新创建一个新的 Spring Boot 项目。

不要随意删除我现有已经可以工作的代码。

不要为了图方便绕过现有 JWT 和 Spring Security。

优先复用现有代码，在现有结构上增量开发。

如果现有代码存在明显 Bug，可以修改，但需要保证原有功能仍然正常。

---

# 二、当前后端已知情况

当前技术栈大致为：

- Java 21
- Spring Boot
- Spring MVC
- Spring Security
- JWT
- MyBatis-Plus
- PostgreSQL
- BCryptPasswordEncoder

请以实际 pom.xml 为准，不要因为这里写的版本与项目稍有不同就强行升级依赖。

当前 PostgreSQL 数据库：

intelligent_ticket

目前至少存在：

## app_user

字段大致包括：

- id
- username
- password_hash
- role

角色：

- USER
- ADMIN

## ticket

字段大致包括：

- id
- title
- description
- priority
- status
- user_id
- created_at
- updated_at

priority：

1~5

status：

- OPEN
- IN_PROGRESS
- CLOSED

当前已经实现过：

- 用户注册
- 用户登录
- BCrypt 密码
- JWT Token
- JWT 中保存 username / userId / role
- Spring Security 权限验证
- 工单创建
- 工单查询
- 工单修改
- 工单删除
- 分页
- 普通用户只能查看自己的工单
- 管理员拥有更高权限
- ADMIN 可以修改工单状态
- 非本人访问工单返回 403

请先验证这些功能当前是否仍然正常。

---

# 三、本次最终目标

我要把这个项目变成一个真正可以演示的前后端分离系统。

最终架构：

浏览器
↓
Vue 前端
↓ HTTP / JSON
Spring Boot REST API
↓
Service
↓
MyBatis-Plus
↓
PostgreSQL

项目根目录保持后端项目。

在根目录新增：

frontend/

用于存放前端。

例如：

smart-ticket-system/
├── src/
├── pom.xml
├── README.md
├── frontend/
│   ├── src/
│   ├── package.json
│   └── ...
└── ...

不要把前端代码塞进 Spring Boot Java 目录。

---

# 四、前端技术栈

使用：

- Vue 3
- Vite
- JavaScript
- Vue Router
- Pinia
- Axios
- Element Plus

除非当前仓库已经存在其他成熟前端框架，否则采用以上技术栈。

暂时不要使用 TypeScript，降低项目学习成本。

允许使用 ECharts 制作简单 Dashboard 图表。

页面风格：

企业内部 IT 工单管理系统。

要求：

- 简洁
- 正式
- 现代
- 不要花里胡哨
- 左侧菜单
- 顶部导航栏
- 主内容区域
- 卡片式统计数据
- PC 端优先
- 基础响应式

中文界面。

整个项目统一 UTF-8，防止中文乱码。

---

# 五、必须完成的前端页面

至少完成以下页面。

## 1. 登录页面

/login

字段：

- 用户名
- 密码
- 登录按钮

登录成功：

保存 JWT。

推荐使用 Pinia 管理登录状态。

Axios 自动携带：

Authorization: Bearer TOKEN

登录失败显示后端返回的明确错误信息。

---

# 2. Dashboard 首页

/dashboard

显示：

- 当前用户
- 当前角色
- 工单总数
- OPEN 数量
- IN_PROGRESS 数量
- CLOSED 数量
- 高优先级工单数量

管理员：

显示所有工单统计。

普通用户：

只能显示自己的统计。

增加简单图表，例如：

工单状态分布。

允许根据现有 API 情况决定：

- 前端统计
或者
- 后端新增 Dashboard API

如果后端新增接口更加合理，就新增。

---

# 3. 我的工单

/tickets

表格字段：

- ID
- 标题
- 优先级
- 状态
- 创建时间
- 更新时间
- 操作

支持：

- 分页
- 状态筛选
- 优先级筛选
- 标题关键词查询

普通 USER：

只能看到自己的工单。

ADMIN：

可以看到全部工单。

后端必须保证权限，不能仅靠前端隐藏。

---

# 4. 创建工单

/tickets/create

表单：

标题

描述

优先级：

1
2
3
4
5

优先级必须进行前后端双重校验。

标题不能为空。

提交成功：

提示：

工单创建成功

然后跳转到工单详情页或者工单列表。

---

# 5. 工单详情

/tickets/:id

显示：

- 工单编号
- 标题
- 描述
- 创建用户
- 优先级
- 当前状态
- 创建时间
- 更新时间

如果 USER 查看不是自己的工单：

后端继续返回 403。

前端显示：

无权访问该工单。

不要只做前端判断。

---

# 6. 工单状态流转

ADMIN 在工单详情中可以执行：

OPEN
→
IN_PROGRESS
→
CLOSED

建议使用按钮或下拉菜单。

USER 不允许修改状态。

前端隐藏管理员按钮只是体验层。

真正权限必须仍然通过 Spring Security / 后端控制。

---

# 7. 工单评论 / 处理记录

为项目增加基础沟通能力。

新增：

ticket_comment

建议字段：

- id
- ticket_id
- user_id
- content
- created_at

实现：

GET /tickets/{id}/comments

POST /tickets/{id}/comments

普通用户：

只能给自己的工单留言。

管理员：

可以给任何工单留言。

前端工单详情下面显示类似时间线：

张三：
VPN 一直提示认证失败。

管理员：
已经重新同步账号权限，请重新连接。

张三：
已经恢复，谢谢。

---

# 六、增加工单历史记录

新增表：

ticket_history

建议字段：

- id
- ticket_id
- operator_id
- action
- old_status
- new_status
- created_at

例如：

创建工单

OPEN → IN_PROGRESS

IN_PROGRESS → CLOSED

管理员修改状态时自动记录。

工单详情页展示处理历史。

不要依靠测试 SQL 手工创建历史，正式业务操作也必须能产生 history。

---

# 七、完成知识库模块

因为项目名称是：

“企业智能工单与知识协同平台”

所以不能只有工单。

新增：

knowledge_article

字段建议：

- id
- title
- summary
- content
- category
- keywords
- status
- created_by
- created_at
- updated_at

status 可使用：

- PUBLISHED
- DRAFT

至少实现：

GET /knowledge

GET /knowledge/{id}

POST /knowledge

PUT /knowledge/{id}

DELETE /knowledge/{id}

权限：

普通用户：

- 查看 PUBLISHED 知识文章
- 搜索知识文章

管理员：

- 新增
- 修改
- 删除
- 发布

前端增加：

/knowledge

知识库列表页面。

支持：

- 标题查询
- 分类筛选
- 关键词搜索

/knowledge/:id

知识文章详情。

/admin/knowledge/create

管理员新增知识文章。

---

# 八、实现“知识推荐”，但不要伪装成真正 AI

暂时不要为了项目名字强行加入假的 ChatGPT。

先实现：

基于关键词的知识推荐。

例如用户创建工单：

标题：

VPN连接失败

描述：

VPN提示认证失败，无法连接公司网络

后端根据：

- title
- description

与 knowledge_article 的：

- title
- category
- keywords

进行基础关键词匹配。

返回最相关的几篇文章。

例如：

《VPN认证失败排查指南》

《公司VPN客户端配置说明》

可以设计接口：

GET /tickets/{id}/knowledge-recommendations

或者创建工单后直接返回推荐结果。

前端在工单详情显示：

“可能对你有帮助”

并列出推荐知识文章。

必须在 README 中明确：

当前版本采用“关键词匹配推荐”，不是大模型 AI。

后续才可以扩展：

Embedding
RAG
LLM

不要把关键词匹配包装成真正 AI。

---

# 九、数据库测试数据

目前数据库主要只有少量开发测试数据。

本次需要建立较真实的演示数据。

不要简单创建：

test1
test2
aaa
bbb

必须使用企业 IT 支持相关业务数据。

建议至少准备：

## 用户

普通用户至少：

zhangsan

lisi

wangwu

管理员：

admin1

密码不要明文存储到数据库。

必须使用 BCrypt。

如果使用 Java 初始化数据，调用 PasswordEncoder。

推荐增加：

dev 环境的数据初始化器。

例如：

DataInitializer

并通过：

@Profile("dev")

限制只在开发环境运行。

同时必须做到：

幂等。

即：

项目启动第二次不能重复插入几十条相同数据。

---

# 十、工单演示数据

至少生成约 20~30 条合理的企业 IT 工单。

主题可以包括：

- VPN 无法连接
- VPN 认证失败
- OA 系统登录失败
- 企业邮箱无法发送邮件
- 邮箱密码重置
- 打印机无法打印
- 办公电脑蓝屏
- 无线网络连接异常
- 公司 Wi-Fi 信号不稳定
- GitLab 权限申请
- Jenkins 构建失败
- 数据库连接失败
- PostgreSQL 连接数过高
- 服务器磁盘空间不足
- 文件服务器无法访问
- 新员工账号开通
- 离职员工账号禁用
- 共享文件夹权限申请
- 软件安装权限申请
- ERP 页面加载异常
- DNS 解析异常
- 内网服务访问超时
- SSL 证书即将过期
- Redis 服务异常
- 应用服务器 CPU 占用过高

数据要覆盖：

priority：

1~5

status：

OPEN
IN_PROGRESS
CLOSED

多个不同用户。

不同创建时间。

部分工单带评论。

部分工单带状态历史。

不要所有数据完全一样。

---

# 十一、知识库演示数据

至少添加约 10~15 篇比较真实的知识文章。

例如：

《VPN认证失败排查指南》

《公司VPN客户端安装与配置》

《企业邮箱密码重置方法》

《OA系统登录异常处理》

《Windows网络重置操作指南》

《打印机无法连接排查方法》

《GitLab访问权限申请流程》

《PostgreSQL连接异常排查》

《服务器磁盘空间不足处理流程》

《DNS解析故障排查》

《Jenkins构建失败常见原因》

《共享文件夹权限申请指南》

每篇文章不能只有标题。

至少包含：

- title
- summary
- category
- keywords
- content

content 写成比较像真正企业知识库的文章。

可以有：

问题现象

可能原因

排查步骤

解决方法

注意事项

---

# 十二、统一异常处理

检查当前异常处理。

如果没有统一异常处理，则建立：

GlobalExceptionHandler

例如处理：

- 参数校验失败
- 未登录
- JWT 无效
- 403
- 用户不存在
- 工单不存在
- 非法 priority
- 非法 status
- 数据库错误

返回统一 JSON。

例如：

{
  "code": 400,
  "message": "优先级必须在1到5之间",
  "data": null
}

前端统一读取 message。

不要把 Java 堆栈信息直接返回给前端。

---

# 十三、API 返回结构

如果当前项目已有稳定返回格式，尽量保持。

如果目前 Controller 返回非常混乱，可以在不破坏大量代码的情况下逐步统一。

例如：

{
  "code": 200,
  "message": "success",
  "data": {}
}

不要为了统一返回格式导致原本所有接口都不可用。

优先保证功能稳定。

---

# 十四、前端登录状态处理

实现：

Axios request interceptor。

自动添加：

Authorization: Bearer xxx

如果后端返回：

401

则：

- 清除 Token
- 清除当前用户信息
- 跳转 /login

如果：

403

显示：

没有权限执行此操作。

Pinia 中管理：

- token
- userId
- username
- role

刷新页面后仍然可以恢复登录状态。

---

# 十五、路由权限

Vue Router：

未登录：

访问任何业务页面 → /login

已登录：

访问 /login → /dashboard

USER：

不能访问管理员页面。

ADMIN：

可以访问：

- 工单管理
- 知识库管理

但是再次强调：

前端路由权限只是 UI 层。

真正权限必须由后端保证。

---

# 十六、管理员工单管理

增加：

/admin/tickets

ADMIN 可以：

- 查看全部工单
- 根据状态筛选
- 根据优先级筛选
- 根据用户名筛选
- 搜索标题
- 查看详情
- 修改状态

表格使用分页。

如果后端现有分页接口不能支持这些条件：

在保留旧接口兼容性的前提下合理扩展。

---

# 十七、Dashboard 数据

管理员 Dashboard 建议显示：

- 总工单数
- 待处理
- 处理中
- 已关闭
- 高优先级
- 用户数量
- 知识文章数量

普通用户 Dashboard：

- 我的全部工单
- 我的待处理
- 我的处理中
- 我的已关闭

可以加入：

工单状态饼图

或：

最近工单列表

不要为了展示图表制造虚假数据。

必须使用 PostgreSQL 中真实数据。

---

# 十八、时间字段

检查 Ticket Entity 当前是否缺少：

createdAt

updatedAt

如果数据库有而 Entity 没有：

补齐。

MyBatis-Plus 映射正确。

创建时间和更新时间正常维护。

所有表尽量包含：

created_at

updated_at

适用时自动更新时间。

前端统一进行正常日期格式化。

---

# 十九、数据库安全与初始化

不要执行：

DROP DATABASE

不要删除我已有数据库。

不要随意 DROP 已有表。

如果需要新增表：

使用安全 SQL。

如果需要 ALTER：

先检查字段是否已经存在。

数据库变更脚本放到清晰的位置，例如：

sql/

包括：

schema.sql

seed.sql

或者采用当前项目已经存在的初始化方式。

如果采用 Java DataInitializer，则仍然建议保留数据库结构 SQL 方便学习。

重要：

不要破坏现有数据。

---

# 二十、README 必须重写完善

项目完成后更新 README.md。

必须包括：

# 项目介绍

企业智能工单与知识协同平台。

# 技术栈

后端：

Java
Spring Boot
Spring Security
JWT
MyBatis-Plus
PostgreSQL

前端：

Vue 3
Vite
Element Plus
Axios
Pinia
Vue Router

# 项目功能

用户：

- 登录
- 创建工单
- 我的工单
- 工单详情
- 评论
- 查看知识库
- 知识推荐

管理员：

- 所有工单管理
- 状态流转
- 评论回复
- 知识库管理
- Dashboard

# 系统架构

简单画出：

Browser
→ Vue
→ Spring Boot
→ MyBatis-Plus
→ PostgreSQL

# 数据库结构

说明各张核心表及关系。

# 权限设计

USER / ADMIN。

# JWT 流程

简要说明。

# 启动方式

PostgreSQL

后端：

mvn spring-boot:run

前端：

cd frontend
npm install
npm run dev

写清楚实际命令。

# 测试账号

只允许 README 中显示开发环境测试账号。

数据库仍然必须存 BCrypt Hash。

# 知识推荐

明确说明：

当前版本使用关键词匹配。

未来可以扩展：

向量数据库 + Embedding + RAG + LLM。

---

# 二十一、代码质量要求

不要：

- 把所有代码写在 Controller
- 大量复制粘贴
- 硬编码 userId
- 硬编码 role
- 硬编码 Token
- 前端假数据
- 用 setTimeout 模拟请求
- 用 JSON 文件假装数据库
- 为了演示绕过 Spring Security
- 数据全部写死在 Vue
- 留大量 TODO
- 写出根本无法运行的伪代码

必须：

Controller

→ Service

→ Mapper

职责清晰。

实体与数据库字段正确对应。

异常正确处理。

权限由后端保证。

---

# 二十二、开发过程要求

请直接开始工作。

不要先生成几千字计划然后停止。

正确工作方式：

1. 阅读当前项目
2. 总结已有架构
3. 编译当前后端
4. 修复已有问题
5. 检查数据库连接
6. 设计缺失的数据表
7. 完善后端接口
8. 创建测试数据
9. 创建 Vue 前端
10. 对接登录
11. 对接工单
12. 对接 Dashboard
13. 对接知识库
14. 完成权限
15. 完成评论和历史
16. 前后端联调
17. 修复报错
18. 重新 build
19. 更新 README
20. 给出最终完成报告

不要每完成一个文件都问我是否继续。

合理问题请自行判断。

只有真正缺失：

- 数据库密码
- 无法推断的重要配置
- 系统权限

这种外部信息时才告诉我。

---

# 二十三、每一步必须真实验证

不能只生成代码然后说“完成”。

后端至少执行：

mvn test

或者：

mvn clean package

确保编译通过。

前端执行：

npm install

npm run build

确保构建成功。

如果出现错误：

读取错误

定位问题

修复

再次运行。

直到构建通过或遇到无法由代码解决的外部阻塞。

---

# 二十四、接口联调

至少实际验证这些流程：

## 普通用户

登录

↓

获取 JWT

↓

创建工单

↓

查看自己的工单

↓

查看详情

↓

添加评论

↓

查看推荐知识

## 权限测试

用户 A

↓

访问用户 B 工单

↓

403

## 管理员

登录

↓

查看全部工单

↓

打开工单

↓

OPEN → IN_PROGRESS

↓

写入 ticket_history

↓

发表评论

↓

IN_PROGRESS → CLOSED

## 知识库

普通用户：

查询文章

管理员：

新增文章

修改文章

发布文章

---

# 二十五、CORS

前端 Vite 默认可能运行：

http://localhost:5173

后端：

http://localhost:8080

正确配置开发环境 CORS。

只允许合理来源。

不要简单把生产配置写成完全开放。

如果使用 Vite proxy 更适合当前结构，可以合理配置。

---

# 二十六、最终 UI

左侧菜单可以设计为：

首页

工单管理
  - 我的工单
  - 创建工单

知识中心
  - 知识库

管理员登录时额外显示：

管理中心
  - 全部工单
  - 知识库管理

顶部：

系统名称

当前用户名

当前角色

退出登录

退出：

清除 Token

返回登录页。

---

# 二十七、简历价值

项目实现时，请优先体现以下 Java 后端能力：

- RESTful API
- Spring Boot
- Spring Security
- JWT
- RBAC
- MyBatis-Plus
- PostgreSQL
- 分页
- 条件查询
- 数据权限
- 参数校验
- 自定义异常
- 全局异常处理
- 状态流转
- 数据库表关联
- 前后端分离
- Vue
- Axios
- 完整业务流程

不要为了炫技引入：

Redis
Kafka
RabbitMQ
Elasticsearch
Docker
Kubernetes
微服务

除非项目确实已经需要。

当前目标优先：

完整、稳定、能讲明白。

不是技术堆砌。

---

# 二十八、不要过度包装

项目必须保持真实。

例如：

关键词匹配

就写：

“基于关键词匹配实现工单知识推荐”。

不要写成：

“自主研发 AI 智能决策引擎”。

JWT 权限：

真实描述。

数据库：

真实描述。

前端：

真实描述。

这个项目以后我要用于：

简历

面试

代码讲解

所以每个功能我要最终能够理解。

---

# 二十九、完成以后输出报告

最终请给我：

## 1. 项目目录结构

列出主要文件。

## 2. 完成的功能

哪些以前就有。

哪些是本次新增。

## 3. 数据库

有哪些表。

每张表作用是什么。

表之间是什么关系。

## 4. API

列出主要 API：

Method

URL

权限

作用。

## 5. 前端页面

列出：

URL

页面功能

权限。

## 6. 测试账号

开发环境测试账号。

## 7. 如何运行

一步一步写清楚：

PostgreSQL

Spring Boot

Vue。

## 8. 验证结果

后端 build 是否成功。

前端 build 是否成功。

哪些接口已经验证。

## 9. 尚未完成的问题

如果确实存在，明确说明。

不要把没有完成的东西说成已经完成。

## 10. 给初学者的代码导读

最后告诉我：

如果我要真正学懂这个项目，建议按照什么顺序阅读代码。

例如：

Entity
→ Mapper
→ Service
→ Controller
→ Security
→ JWT
→ 前端 Axios
→ Vue 页面

---

# 三十、最终验收标准

项目最终必须至少达到：

✅ PostgreSQL 真正存储数据

✅ 有较丰富企业 IT 工单测试数据

✅ 有用户和管理员

✅ BCrypt 密码

✅ JWT 登录

✅ USER / ADMIN 权限

✅ 普通用户数据隔离

✅ 工单 CRUD

✅ 工单分页和筛选

✅ 工单状态流转

✅ 工单评论

✅ 工单历史

✅ 知识库

✅ 关键词知识推荐

✅ Vue 前端

✅ 登录页面

✅ Dashboard

✅ 工单列表

✅ 创建工单

✅ 工单详情

✅ 管理员管理页面

✅ 知识库页面

✅ 前后端真正联调

✅ 页面数据来源于 PostgreSQL，而不是前端假数据

✅ 后端编译通过

✅ 前端 build 通过

✅ README 完整

最终目标：

我启动 PostgreSQL、Spring Boot 和 Vue 以后，可以直接打开浏览器登录系统，从创建工单一直操作到管理员处理完成，并能看到数据库中的真实数据变化。

请现在先检查当前仓库实际情况，然后直接开始实施，不要重新创建整个项目，不要停留在方案阶段。