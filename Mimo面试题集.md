# 软件测试实习生面试题集

> 基于简历：李俊卓 | 软件测试实习生 | 27应届 | 内江师范学院 计算机科学与技术
> 准备时间：2026年7月

---

## 使用说明

本面试题分为四大模块：

- **技术基础**：测试理论 + Java/Python + 工具链
- **项目深挖**：针对简历中 3 个项目的追问
- **场景设计**：给你一个场景，现场设计测试方案
- **行为面**：软技能与团队协作

每道题后附参考答案要点（加粗为面试官加分点）。

---

## 一、技术基础

### 1.1 测试理论

**Q1: 请解释黑盒测试和白盒测试的区别，各举一个你实际用到的例子。**

> **参考答案**：
> - 黑盒测试：不关心内部实现，只验证输入输出。例如：用 Postman 调用 `/api/auth/login`，传不同用户名密码验证返回结果
> - 白盒测试：知道代码逻辑，针对性设计用例。例如：读 Spring Security 配置，知道 `@PreAuthorize` 注解用 `hasRole()` 会有前缀 bug，针对性测权限边界
> - **加分**：提到灰盒测试——结合两者，比如通过 API 测试验证数据库状态变化

**Q2: 测试用例设计方法你知道哪些？怎么用？**

> **参考答案**：
> - 等价类划分：把输入分为有效/无效等价类，每类挑代表值。例：登录用户名（正确、不存在、空字符串、超长）
> - 边界值分析：测试边界条件。例：Issue 标题长度限制 100 字符，测 0、1、100、101
> - 场景法：模拟完整业务流程。例：用户注册→创建团队→创建项目→创建 Issue→拖拽到完成
> - 错误推测法：基于经验猜测可能出错的点。例：WebSocket 断连后重连时数据是否一致
> - **加分**：提到项目中直接用参数化测试（`@pytest.mark.parametrize` + JSON 数据文件）实践这些方法

**Q3: 缺陷的生命周期是怎样的？你用什么工具管理？**

> **参考答案**：
> ```
> 发现 → 提交(New) → 确认(Open) → 分配(Assigned) → 修复(Fixed) → 
> 验证(Verified) → 关闭(Closed)
>                       ↕
>                     重新打开(Reopened)
>                       ↕  
>                     拒绝(Rejected)
> ```
> - Mimo 项目本身就是一个 Issue 管理平台，可以用它管理缺陷
> - **加分**：提到缺陷报告的要素——标题、复现步骤、预期/实际结果、优先级、严重程度、环境信息、截图/日志

**Q4: 什么是回归测试？为什么需要自动化回归？**

> **参考答案**：
> - 回归测试：修改代码后，重新运行已有测试确保原有功能没被破坏
> - 自动化必要：手工回归耗时长、容易遗漏。每次 git push 触发 Jenkins 跑一遍测试套件，几分钟出结果
> - **加分**：Mimo 70+ API 测试用例 + E2E 看板拖拽测试，每次提交自动回归

### 1.2 Python 基础

**Q5: Pytest 的 fixture 机制是怎么回事？scope 参数有什么用？**

> **参考答案**：
> ```python
> @pytest.fixture(scope="function")  # 每个测试函数一个实例
> def page(browser):
>     context = browser.new_context()
>     page = context.new_page()
>     yield page          # 测试函数拿到 page
>     context.close()      # 测试结束后自动清理
> ```
> - scope 可选值：`function`(默认)、`class`、`module`、`session`
> - `function`：最常用，测试间完全隔离
> - `session`：整个测试跑一次，适合初始化数据库连接
> - `conftest.py` 中的 fixture 自动全局可用，不需显式 import
> - **加分**：提到 `yield` 实现 setUp/tearDown 模式

**Q6: 你项目中 requests.Session() 和直接 requests.get() 有什么区别？**

> **参考答案**：
> ```python
> # 不用 Session：每次请求新建 TCP 连接
> requests.post(url1, json=data1)
> requests.get(url2)          # 又建新连接
>
> # 用 Session：复用连接
> session = requests.Session()
> session.post(url1, json=data1)  # 建立连接
> session.get(url2)               # 复用同一连接
> ```
> - Session 自动保存 Cookie
> - 底层连接池复用，性能更好
> - Mimo 的 `MimoAPIClient` 就是基于 Session 的，登录后 token 在后续请求中自动传递
> - **加分**：提到 HTTP Keep-Alive

**Q7: Python 的 `__init__.py` 文件有什么用？**

> **参考答案**：
> - 标识目录为 Python 包，可以被 `import`
> - 可以控制 `from package import *` 暴露的内容（`__all__`）
> - 可以在包导入时执行初始化代码
> - `st2/api/__init__.py` 存在使得可以 `from api import MimoAPI`

### 1.3 Java 基础

**Q8: Spring Boot 中怎么实现接口鉴权？**

> **参考答案**：
> - Mimo 使用 Spring Security + JWT
> - 流程：用户登录 → 后端生成 JWT Token → 前端存 localStorage → 后续请求带 `Authorization: Bearer <token>` → `JwtAuthenticationFilter` 拦截验证
> - 公开接口在 `SecurityConfig` 中配置白名单（如 `/api/auth/login`、`/api/auth/register`）
> - **加分**：能说出 `@PreAuthorize` 和 `hasRole/hasAuthority` 的区别，以及已知的 `hasRole` 前缀 bug

**Q9: MyBatis-Plus 的 `LambdaQueryWrapper` 是做什么的？**

> **参考答案**：
> - 链式构建查询条件，避免硬编码字段名
> - `new LambdaQueryWrapper<Issue>().eq(Issue::getProjectId, projectId)` 
> - vs 手写 SQL：类型安全、重构友好、字段改名自动跟随
> - **加分**：提到逻辑删除 `@TableLogic` 注解

**Q10: Mimo 为什么选择 Spring Boot 2.7 + Java 17，而不是最新版？**

> **参考答案**：
> - Spring Boot 2.7 是 Spring Boot 2.x 的最后一个功能版本，稳定成熟
> - Spring Boot 3.x 需要 Java 17+ 和 Jakarta EE 迁移，2.7 使用 javax 命名空间
> - 兼容现有依赖生态（MyBatis-Plus、Knife4j 等）
> - Java 17 是 LTS 版本，长期支持

### 1.4 测试工具链

**Q11: 从 Selenium 迁移到 Playwright 解决了什么问题？**

> **参考答案**：
> - chromedriver 版本管理：Selenium 需要手动下载匹配版本，经常不兼容 → Playwright 一键安装
> - 元素等待：Selenium 需要写 `WebDriverWait` → Playwright 自动等待
> - 浏览器隔离：Selenium 需要手动管理窗口 → Playwright Context 天然隔离
> - 执行速度：提升 40%+
> - 调试：Selenium 靠截图 → Playwright Trace Viewer 时间轴回放
> - WebSocket 测试：Selenium 无法直接操作 → Playwright 内置支持
> - **加分**：提到 Mimo 看板协同功能（WebSocket 实时同步）是推动迁移的关键原因

**Q12: Postman 和 Apifox 有什么区别？你更推荐哪个？**

> **参考答案**：
> - Postman：老牌 API 测试工具，团队协作需付费
> - Apifox：国产，集 API 文档 + 调试 + Mock + 自动化测试于一体
> - 实际使用：用 Apifox 做接口调试和文档管理，用 Python + Requests 做自动化回归
> - **加分**：Apifox 支持导出为各种格式，中文用户友好

**Q13: JMeter 做性能测试时关注哪些指标？**

> **参考答案**：
> - QPS（每秒查询数）/ TPS（每秒事务数）
> - 响应时间（平均、P95、P99、最大）
> - 吞吐量
> - 错误率
> - 服务器资源：CPU、内存、网络 IO
> - **加分**：Mimo 项目测试过"多用户同时操作看板"场景，分析瓶颈在数据库查询还是网络

**Q14: Allure 报告能展示哪些信息？**

> **参考答案**：
> - 总览：通过率、失败数、执行时间
> - 按功能模块（`@allure.feature`）和用户故事（`@allure.story`）分组
> - 失败用例的详细步骤、截图、日志
> - 历史趋势（和上次运行的对比）
> - **加分**：`@allure.step()` 可以自定义步骤名，报告中显示可读的步骤链

### 1.5 AI 工具应用（简历亮点）

**Q15: 你简历提到用 AI 提升测试效率，具体怎么做的？**

> **参考答案**：
> - **用例生成**：把需求文档/接口文档给 AI，让它生成参数化测试数据 JSON
> - **脚本生成**：描述业务场景，AI 生成 Playwright/Pytest 初始脚本，人工微调
> - **缺陷分析**：把报错日志给 AI，辅助定位根因
> - **代码审查**：用 Claude Code 检查测试代码覆盖率、断言完整性
> - **文档生成**：接口文档交给 AI 生成测试用例清单
> - **加分**：强调 AI 是辅助工具，核心判断和设计仍需人工，不能盲目信任

**Q16: 你日常使用哪些 AI 工具？分别用在什么场景？**

> **参考答案**：
> - Claude Code：代码生成、Bug 定位、项目理解
> - DeepSeek：API 成本低，批量生成测试数据
> - ChatGPT：写文档、整理思路
> - Cursor：AI 辅助 IDE，写代码时实时补全
> - **加分**：不同工具各有优势，根据场景选择

---

## 二、项目深挖

### 2.1 Mimo 项目（测试开发，2026/05-至今）

**Q17: 详细介绍一下 Mimo 项目的测试架构。**

> **参考答案**：
> ```
> st2/ — Playwright + Pytest + Allure + Requests
> ├── conftest.py          # Fixture 层（浏览器、API 客户端）
> ├── base/base_page.py    # BasePage（Playwright 通用操作封装）
> ├── pages/               # PO 模式页面对象
> ├── api/                 # API 客户端（Session + JWT 管理）
> ├── scripts/             # 测试用例（E2E + API）
> └── data/                # JSON 参数化测试数据
> ```
> - 3 层架构：核心客户端层 → API/Page 封装层 → 测试用例层
> - API 测试覆盖 11 个模块、70+ 用例
> - E2E 测试覆盖登录、注册、看板拖拽核心场景
> - **加分**：说出从 Selenium 迁移到 Playwright 的具体过程和技术决策

**Q18: Mimo 看板拖拽功能你怎么测试的？**

> **参考答案**：
> - **API 层**：调用 `PUT /api/board/issue/move`，验证 Issue 状态从 TODO→IN_PROGRESS，数据库确认
> - **E2E 层**：Playwright 的 `drag_to()` 模拟真实拖拽，验证 UI 刷新
> - **WebSocket**：验证拖拽后其他用户在同一看板收到实时更新
> - **异常场景**：拖到非法区域（验证拒绝）、网络断开时拖拽
> - **加分**：Playwright 原生支持 WebSocket，可以直接监听 `/topic/board/{projectId}` 验证广播消息

**Q19: Mimo 的权限体系你怎么测试的？**

> **参考答案**：
> - 两个角色：`ROLE_ADMIN`（管理员）和 `ROLE_MEMBER`（普通成员）
> - 用不同 fixture 模拟不同角色：`api_client_admin`、`api_client_member`
> - 测试点：
>   - 成员不能邀请/移除团队成员（403）
>   - 成员不能删除他人创建的项目
>   - 未登录不能访问任何 `/api/**` 接口（401）
>   - 发现并验证了 `hasRole('ROLE_ADMIN')` 前缀 bug
> - **加分**：提到 `@PreAuthorize` 的 `hasRole()` vs `hasAuthority()` 的区别及 bug

**Q20: WebSocket 实时通信怎么测试？**

> **参考答案**：
> - Playwright 内置 `page.wait_for_websocket()` 可以监控 WS 连接
> - 测试流程：用户A 操作看板 → 后端通过 STOMP 广播 → Playwright 监听用户B 的页面 → 验证收到 `ISSUE_MOVED` 事件
> - API 层可以 mock WebSocket 消息验证广播逻辑
> - 异常测试：断网重连、token 过期
> - **加分**：Mimo 用 STOMP over SockJS 协议，比原生 WebSocket 兼容性更好

### 2.2 电商平台项目（自动化测试，2026/02-2026/03）

**Q21: 电商项目的 PO 模式你是怎么设计的？**

> **参考答案**：
> - 每个页面一个类：LoginPage、ProductListPage、CartPage、OrderPage
> - 基类封装通用操作：find_element、click、input、screenshot
> - 业务方法组合原子操作：`add_to_cart(product_name)` = 搜索 → 选择 → 点击加入购物车
> - 测试用例只调业务方法，不接触选择器
> - **加分**：PO 模式下 UI 变更只需改页面类，测试用例不用动

**Q22: Android 端自动化测试你怎么做的？**

> **参考答案**：
> - 使用 Appium + Python
> - 通过 `desired_caps` 配置设备信息（platformName、deviceName、appPackage 等）
> - 核心操作路径：登录 → 浏览商品 → 加入购物车 → 下单
> - 实现 Web + Android 双端覆盖
> - **加分**：Appium 支持跨平台（iOS + Android），一套脚本适配两端

### 2.3 CRM 系统项目（接口测试，2026/03-2026/04）

**Q23: Dubbo RPC 接口和 HTTP 接口测试有什么区别？**

> **参考答案**：
> - HTTP：基于 REST，用 Postman/Apifox/Requests 直接调用
> - Dubbo RPC：需要 Dubbo 客户端（dubboclient）或泛化调用，基于 TCP 二进制协议
> - 参数序列化方式不同（Dubbo 用 Hessian/Protobuf）
> - 需要知道服务名、方法名、参数类型才能调用
> - **加分**：Dubbo 接口测试需要关注超时、重试、负载均衡等 RPC 特性

**Q24: 你怎么用 AI 提高 Dubbo 接口测试效率的？**

> **参考答案**：
> - AI 根据接口文档生成调用参数的 JSON 模板
> - AI 分析 Dubbo 接口返回数据，辅助判断业务逻辑正确性
> - AI 生成参数化测试的不同场景数据（正常值、null、空字符串、超大值等）
> - **加分**：强调 AI 辅助判断边界值

---

## 三、场景设计题

**Q25: 给你一个新的登录功能，请现场设计测试用例。**

> **回答思路**：
> 
> **功能测试**：
> - 正确的用户名+密码 → 登录成功，跳转主页，返回 token
> - 错误的密码 → 提示密码错误
> - 不存在的用户名 → 提示用户不存在
> - 空用户名/空密码 → 前端校验提示
> - 记住密码功能 → 勾选后关闭浏览器再打开仍保持登录
> 
> **安全测试**：
> - SQL 注入：用户名输入 `' OR '1'='1` → 不能绕过
> - XSS：用户名输入 `<script>alert(1)</script>` → 被转义
> - 密码传输是否加密（HTTPS）
> - 暴力破解防护：连续失败多次是否锁定
> 
> **兼容性测试**：
> - Chrome / Firefox / Edge
> - 不同分辨率下登录按钮是否可见
> 
> **性能测试**：
> - 100 并发登录的响应时间
> 
> **加分**：提到用等价类划分和边界值分析组织用例，用参数化测试实现

**Q26: Mimo 上线后用户反馈"看板拖拽有时不生效"，你会怎么排查？**

> **回答思路**：
> 1. **复现问题**：获取用户的浏览器、操作步骤、网络环境
> 2. **前端排查**：打开浏览器 DevTools，检查拖拽事件和 API 请求
> 3. **后端排查**：查看 `/api/board/issue/move` 是否收到请求、返回什么
> 4. **WebSocket**：检查 STOMP 连接状态，广播是否发出
> 5. **数据库**：确认 Issue 状态是否真的更新了
> 6. **写回归测试**：新增对应场景的自动化用例
> 7. **加分**：提出用 Playwright Trace Viewer 录下拖拽全过程回放

**Q27: 假设你要给一个没有文档的 API 写测试，你怎么做？**

> **回答思路**：
> 1. 用浏览器 DevTools Network 面板抓请求，看 URL、参数、响应
> 2. 用 Swagger/Knife4j 页面查看（如果有的话）
> 3. 先发包看响应，逐步推断字段含义
> 4. 用 Postman/Apifox 手工探索 → 形成理解 → 写自动化脚本
> 5. 做边界探索：空值、超长值、特殊字符
> 6. **加分**：把逆向出的接口文档化，帮助后续测试

---

## 四、行为面试

**Q28: 你简历提到"专业排名前5%"，同时做了 3 个完整测试项目，怎么平衡的？**

> **参考答案思路**：
> - 课堂知识直接用在项目中（学以致用）
> - AI 工具提高效率，节省重复工作时间
> - 时间管理上，利用假期和课余时间集中开发
> - 强调学习驱动实践，实践巩固学习

**Q29: 你在团队中最擅长的角色是什么？**

> **参考答案思路**：
> - 基于简历定位：测试开发，关注质量保障
> - 擅长发现边界条件和异常场景
> - 善于用 AI 工具提高团队效率
> - 能够独立搭建自动化测试框架
> - 协作经验：与开发者有效沟通缺陷、推动修复

**Q30: 遇到一个你不会的技术，你通常怎么学习？**

> **参考答案思路**：
> 1. 先用 AI 工具快速了解概念和基础用法
> 2. 看官方文档的 Quick Start
> 3. 在现有项目中找类似实现参考
> 4. 动手写 Demo 验证理解
> 5. 遇到具体问题再查 Stack Overflow / GitHub Issues
> - **加分**：举例——从 Selenium 学 Playwright，先用 AI 对比两者的 API，再写迁移脚本

**Q31: 你为什么要从事软件测试？不是开发？**

> **参考答案思路**：
> - 对质量有追求，发现 bug 有成就感
> - 测试需要广度和深度：既要懂业务又要懂技术
> - 适合自己"严谨细致"的特点
> - 测试开发不是"低配开发"，是质量保障的专业方向
> - 自动化测试 + AI 让测试越来越有技术含量
> - **加分**：对测试行业趋势有见解（测试左移、持续测试、AI 辅助）

**Q32: 说说你最大的优点和缺点。**

> **参考答案思路**：
> - 优点：学习能力强（排名前5%）、善于利用 AI 工具提高效率、严谨细致
> - 缺点（真实但可接受）：实际工作经验不足，需要在真实生产环境锻炼；有时过于追求完美导致进度延迟，正在学习平衡质量与效率

---

## 五、面试准备建议

### 技术准备优先级

1. **必会**：测试理论（黑盒/白盒、等价类/边界值/场景法）、缺陷生命周期、Pytest 基础
2. **重点**：Playwright 核心概念（自动等待、Context、定位器）、API 测试框架设计
3. **加分**：CI/CD Jenkins 集成、WebSocket 测试、AI 工具应用
4. **了解**：JMeter 性能测试概念、Spring Security 权限模型

### 项目描述模板

面试中描述 Mimo 项目建议用 STAR 法则：

- **Situation**：Mimo 是一个类 Trello 的项目管理平台，包含看板、Sprint、实时协作等核心功能，共 39 个 API 接口
- **Task**：搭建完整的自动化测试体系，覆盖回归测试和核心业务流程
- **Action**：搭建 Playwright + Pytest + Allure + Requests 框架，3 层 PO 架构；从 Selenium 迁移到 Playwright 提升 40%+ 执行速度；编写 70+ API 测试用例和 E2E 用例
- **Result**：实现每次代码提交自动回归，可视化报告可追踪历史趋势

### 可能的反问环节

- "你们团队测试开发比是多少？"
- "测试用例评审流程是怎样的？"
- "用哪些工具管理测试用例和缺陷？"
- "实习生有 mentor 带吗？"
- "入职第一个月期望我做什么？"

---

## 速查卡片（打印携带）

```
┌─────────────────────────────────────────┐
│  面试速查卡                              │
├─────────────────────────────────────────┤
│  PO 模式：BasePage → PageObject → Test  │
│  Pytest fixture：yield 前=前置 后=清理   │
│  Session vs Request：连接复用 + Cookie   │
│  Playwright 优势：自动等待、Context、WS  │
│  参数化：@parametrize + JSON 数据文件     │
│  Mimo 技术栈：SB 2.7 + Vue3 + MySQL + WS│
│  测试覆盖：11模块、70+ API、E2E 核心场景  │
│  AI 工具：Claude Code/DeepSeek/Cursor   │
│  权限：ADMIN vs MEMBER + JWT 鉴权        │
│  速度提升：Playwright 比 Selenium 快40%  │
└─────────────────────────────────────────┘
```
