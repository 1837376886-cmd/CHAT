# 在线客服功能需求计划


## 1. 背景与目标

### 1.1 背景
项目已具备一套基于 Netty WebSocket 的即时聊天系统（私聊 + 群聊 + 表情消息），拥有完整的会话管理、消息存储、在线状态追踪能力。在此基础上扩展在线客服功能，可以最大化复用现有基础设施。

### 1.2 目标
- 访客（匿名用户）在指定 PC 页面右下角点击客服图标，系统自动分配一名在线客服，建立 1 对 1 会话。
- 客服人员在后台管理工作台中处理访客咨询。
- 管理员可在后台配置客服人员、查看会话记录。

---

## 2. 功能模块与优先级

### P0 — 核心 MVP（最小可用版本）
| 模块 | 说明 |
|------|------|
| **客服角色配置** | 在现有用户管理中增加"客服"标记；具备该身份的用户才能被分配接待访客。 |
| **访客接入与分配引擎** | 访客匿名访问 → 生成访客身份 → **优先分配上次会话的客服**，若不可用则按"在线且当前接待数最少"原则自动分配 → 创建客服会话。 |
| **接待上限话术兜底** | 所有客服均达到最大接待数时，**不进入排队**，直接自动回复等待话术（如"当前咨询繁忙，请稍候，客服稍后将为您服务"），由系统消息发送给访客；访客继续保持连接，待有客服释放容量后由后端补发系统消息提示并完成分配。 |
| **访客端聊天窗口** | PC 页面右下角悬浮客服入口 → 点击弹出聊天窗口（可复用现有 ChatBox 组件做简化版）。 |
| **客服工作台** | 后台独立页面，展示当前接待的访客列表；点击访客卡片进入聊天（复用现有 ChatBox）。 |
| **会话结束** | 客服可主动结束会话；结束后访客若再次咨询走分配引擎，**默认仍分配到上次客服**。 |
| **访客登录后历史关联** | 访客在网站完成登录（绑定到 sys_user）后，**根据 IP 反查 chat_visitor 历史记录**并合并到该用户名下，让其在工作台/个人中心可见此前匿名时的客服聊天记录。 |

### P1 — 体验增强（建议实现）
| 模块 | 说明 |
|------|------|
| **客服状态管理** | 客服可切换状态：在线 / 忙碌 / 离线；忙碌/离线时不参与自动分配。 |
| **最大接待数限制** | 每个客服可配置最大同时接待数（如 5 人），达到上限后不再分配（触发上文话术兜底）。 |
| **离线兜底** | 无在线客服时，访客端提示"当前无客服在线，请留言"，并支持提交离线留言。 |
| **访客信息展示** | 客服工作台中展示访客基础信息（IP、来源页面、接入时间、是否已登录）。 |
| **客服会话记录查询** | 后台管理页按时间、客服、访客查询历史会话及消息记录。 |

### P2 — 进阶功能（可选）
| 模块 | 说明 |
|------|------|
| **快捷回复语库** | 客服可预设常用回复语，点击一键发送。 |

> **明确移除（按用户要求）**：客服绩效统计、排队机制、会话转接 — 不在本期范围内。

---

## 3. 数据模型设计

### 3.1 现有表复用与变更

**`sys_user` 新增字段**
```sql
ALTER TABLE sys_user ADD COLUMN is_customer_service tinyint DEFAULT 0
  COMMENT '是否客服：0-否，1-是' AFTER status;
```

**`chat_session` 无需变更**（按最终选型，客服会话使用独立表 `cs_session` / `cs_message`，与现有聊天系统完全隔离，避免互相影响。）

### 3.2 新增表

**`chat_visitor` — 访客信息表**
```sql
CREATE TABLE `chat_visitor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_token` varchar(64) NOT NULL COMMENT '访客Token（前端持久化于localStorage）',
  `nickname` varchar(50) DEFAULT '访客' COMMENT '访客昵称',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器UA',
  `source_page` varchar(255) DEFAULT NULL COMMENT '来源页面URL',
  `bound_user_id` bigint DEFAULT NULL COMMENT '已绑定的sys_user.id（登录后回填，用于历史关联）',
  `last_cs_user_id` bigint DEFAULT NULL COMMENT '最近一次接待该访客的客服userId（用于优先分配）',
  `last_session_end_time` datetime DEFAULT NULL COMMENT '最近一次会话结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_visitor_token` (`visitor_token`),
  KEY `idx_ip` (`ip`),
  KEY `idx_bound_user_id` (`bound_user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客信息表';
```
- `last_cs_user_id`：用于实现"再次咨询默认分配上次客服"。
- `bound_user_id` + `idx_ip`：用于"访客登录后按 IP 关联历史记录"。

**`cs_session` — 客服会话表（新建，独立表）**
```sql
CREATE TABLE `cs_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_id` bigint NOT NULL COMMENT '访客ID（chat_visitor.id）',
  `cs_user_id` bigint NOT NULL COMMENT '客服用户ID（sys_user.id）',
  `status` tinyint DEFAULT 1 COMMENT '状态：0-已结束，1-进行中，2-等待中（满员话术兜底）',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '会话结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_visitor_id` (`visitor_id`),
  KEY `idx_cs_user_id` (`cs_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';
```

**`cs_message` — 客服消息表（新建，独立表）**
```sql
CREATE TABLE `cs_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '客服会话ID（cs_session.id）',
  `from_type` tinyint NOT NULL COMMENT '发送者类型：1-访客，2-客服，3-系统',
  `from_user_id` bigint DEFAULT NULL COMMENT '发送者用户ID（客服时有效）',
  `content` text NOT NULL COMMENT '消息内容',
  `msg_type` tinyint DEFAULT 1 COMMENT '消息类型：1-文本，2-图片',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息表';
```

**`cs_config` — 客服配置表（P1阶段）**
```sql
CREATE TABLE `cs_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '客服用户ID',
  `max_sessions` int DEFAULT 5 COMMENT '最大同时接待数',
  `auto_reply` varchar(500) DEFAULT NULL COMMENT '自动回复语',
  `status` tinyint DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服配置表';
```

### 3.3 客服状态存储方案（选型待决策）

**方案 A：内存存储（简单，单节点）**
- 在 `ConnectionManager` 或新建 `CustomerServiceManager` 中维护：
  - `Map<Long, CsStatus>` 客服状态（在线/忙碌/离线）
  - `Map<Long, AtomicInteger>` 当前接待数
- 优点：实现简单，无需外部依赖
- 缺点：多节点部署时状态不共享

**方案 B：Redis 存储（推荐，支持集群）**
- Redis Hash：`cs:status:{userId}` → 在线状态、当前接待数、最后心跳时间
- 访客分配时从 Redis 聚合查询
- 优点：支持多实例、状态持久化、可扩展排队机制
- 缺点：引入 Redis 运维依赖（项目已有 Redis 用于未读数计数，可复用）

最终选型：方案B，使用Redis实现
---

## 4. 核心流程设计

### 4.1 访客接入流程

```
访客打开页面
  ↓
前端生成/读取 visitor_token（存 localStorage）
  ↓
点击客服图标 → 后端 /cs/connect 接口
  ↓
后端：根据 visitor_token 查找/创建 chat_visitor 记录（同时记录 IP）
  ↓
检查该访客是否已有进行中的客服会话
  ├─ 有 → 返回现有 sessionId，直接恢复会话
  └─ 无 → 进入分配引擎（见 4.2）
```

### 4.2 分配引擎（含"上次客服优先"与"满员话术兜底"）

```
分配引擎入口
  ↓
读取 chat_visitor.last_cs_user_id
  ↓
┌───────────────────────────────────────┐
│ 步骤1：上次客服优先                    │
│  若 last_cs_user_id 不为空            │
│    且该客服当前在线                   │
│    且当前接待数 < 最大接待数          │
│  → 直接分配给该客服                   │
└───────────────────────────────────────┘
        ↓ 不满足
┌───────────────────────────────────────┐
│ 步骤2：默认分配                        │
│  查询所有"在线 且 未达上限"的客服      │
│  按 当前接待数 ASC 排序，取第一个     │
│  → 分配                               │
└───────────────────────────────────────┘
        ↓ 全部满员或无在线客服
┌───────────────────────────────────────┐
│ 步骤3：话术兜底（不排队）              │
│  系统消息推送给访客：                  │
│    "当前咨询繁忙，请稍候，客服稍后..." │
│  访客保持 WS 连接，不创建会话；        │
│  服务端订阅"客服容量释放"事件，        │
│  容量空出时再触发分配并通知访客。      │
└───────────────────────────────────────┘
  ↓ 分配成功
创建 chat_session（session_type=3, biz_type=1, visitor_id=访客id）
插入 chat_session_member（客服 userId）
更新 chat_visitor.last_cs_user_id = targetCsUserId
客服接待数 +1
  ↓
返回 { sessionId, csUserId, csNickname }
  ↓
访客端 WebSocket 连接，发送 AUTH（携带 visitor_token）
  ↓
开始聊天
```

> **话术兜底实现要点**：满员时不写入会话，但需把访客的 visitor_token 加入"等待集合"（Redis Set 或内存队列，**仅用于触发再分配，不保证 FIFO，因此非排队**）。当任意客服 `结束会话`/`容量减少` 时，从等待集合中**任取一个**触发分配并推送消息；若访客已离开，移除其 token。

### 4.3 访客匿名接入 → 登录后历史关联

```
场景：访客 A 曾匿名咨询（visitor_token=T1, ip=1.2.3.4），后注册登录系统
  ↓
登录成功钩子（在登录成功的 Service 中插入逻辑）
  ↓
触发：CustomerServiceVisitorService.bindByLogin(userId, currentIp)
  ↓
SQL：UPDATE chat_visitor SET bound_user_id = #{userId}
     WHERE ip = #{currentIp} AND bound_user_id IS NULL
       AND create_time > NOW() - INTERVAL 90 DAY  -- 限制时间窗，避免误关联他人
  ↓
被命中的所有 visitor 记录都被标记为该用户名下
  ↓
用户在 个人中心/我的客服记录 页面：
  GET /cs/my/history
  → SELECT 所有 chat_session WHERE visitor_id IN
    (SELECT id FROM chat_visitor WHERE bound_user_id = #{userId})
```

> **风险与缓解**：仅靠 IP 关联存在多人共用 IP（公司、网吧）的误判风险。建议：
> - 时间窗：仅匹配最近 90 天内的访客记录；
> - 二次确认：在前端显示"匹配到 N 条历史咨询，是否确认是您本人？"，由用户主动确认后再绑定（可选，更稳）；
> - 可后续叠加 `user_agent` / 浏览器指纹做二次校验。

### 4.4 访客端 WebSocket 认证（匿名用户）

现有 WebSocket 认证基于 `fromUserId`（Long，登录用户）。访客匿名接入需要扩展：

**方案：双轨认证**
- 登录用户：沿用现有 `AUTH` 消息，携带 `fromUserId`
- 访客用户：新增 `GUEST_AUTH` 消息类型，携带 `visitor_token`
- `WebSocketHandler` / `ChatChannelHandler` 中识别 `GUEST_AUTH`，从 `chat_visitor` 表加载访客信息，建立连接（userId 可用负数或访客表 id）

**简化方案（推荐）**：访客接入时后端返回一个临时 `guestUserId`（使用访客表 id，但该 id 和 sys_user.id 可能冲突）

**更安全的简化方案**：
- 访客 WebSocket AUTH 时，`fromUserId` 传 `0` 或特定值
- 额外携带 `visitorToken` 字段
- 后端通过 `visitorToken` 识别具体访客
- `chat_message.from_user_id` 对访客统一存 `0`，`from_user_nickname` 存访客昵称
- 客服端显示消息时，如果 `from_user_id == 0` 且会话是客服会话，则判定为访客消息

这个方案改动最小，但缺点是如果以后一个访客同时开多个会话会有歧义。不过客服场景下通常一个访客同一时间只有一个会话，所以可行。

最终选型：双轨认证方案

### 4.5 会话结束流程

```
客服点击"结束会话"
  ↓
调用 /cs/session/close/{sessionId}
  ↓
更新 chat_session.status = 0（已结束）
更新 chat_visitor.last_cs_user_id = 当前客服userId（用于下次优先分配）
更新 chat_visitor.last_session_end_time = NOW()
  ↓
客服接待数 -1
  ↓
触发"等待集合"消费：若有访客在等待，从集合取一人执行分配
  ↓
推送 SYSTEM_NOTICE 给双方："会话已结束"
  ↓
访客端显示"客服已结束会话，如有问题请重新咨询"
```

---

## 5. 后端接口规划

### 5.1 访客端接口（无需登录）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/cs/connect` | POST | 访客接入，返回分配的 session 信息和客服信息；满员时返回 `waiting=true` |
| `/cs/message/send` | POST | 访客发送消息（访客 token 鉴权） |
| `/cs/session/history/{sessionId}` | GET | 获取当前会话历史消息 |

### 5.2 客服端接口（需客服角色权限）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/cs/workbench/sessions` | GET | 获取当前客服的所有进行中的会话列表 |
| `/cs/session/close/{sessionId}` | POST | 客服结束指定会话（触发等待队列消费） |
| `/cs/message/send` | POST | 客服发送消息 |
| `/cs/config` | GET/PUT | 获取/修改个人客服配置（P1） |

### 5.3 已登录用户接口（需登录）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/cs/my/history` | GET | 当前用户名下所有客服历史会话（含登录前 IP 关联到的匿名记录） |
| `/cs/my/history/{sessionId}/messages` | GET | 历史会话消息明细 |

### 5.4 管理端接口（需管理员权限）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/cs/staff/list` | GET | 客服列表（从 sys_user 筛选） |
| `/cs/staff/set` | POST | 设置/取消用户客服身份 |
| `/cs/sessions` | GET | 所有客服会话查询（P1） |

> **接口删减**：原计划中的 `/cs/stats` 客服绩效统计接口已按要求移除。

---

## 6. 前端页面规划

### 6.1 访客端（独立页面/嵌入组件）

**文件位置**：`ruoyi-ui/src/components/CustomerService/GuestChatWidget.vue`

**功能**：
- 以 Vue 组件形式提供，可被任何 PC 页面嵌入
- 右下角固定悬浮按钮（气泡图标 + 未读红点）
- 点击后向上弹出一个迷你聊天窗口（高度约 500px，宽度约 360px）
- 聊天窗口内嵌简化版 ChatBox：
  - 不显示左侧会话列表（只有一个会话）
  - 顶部显示客服昵称和在线状态
  - 支持文本消息发送
  - 不需要新建群聊、表情面板可选
- 自动调用 `/cs/connect` 获取 session，建立 WebSocket
- **满员场景**：当 `/cs/connect` 返回 `waiting=true` 时，聊天区域只展示系统消息"当前咨询繁忙，请稍候..."，禁用输入框；收到 WS 推送的"已为您分配客服"系统消息后启用输入框。

**使用方式**：目标 PC 页面引入组件 `<GuestChatWidget />`

### 6.2 客服工作台（后台管理页面）

**文件位置**：`ruoyi-ui/src/views/cs/workbench/index.vue`

**功能**：
- 左侧：当前接待的访客列表（卡片形式，显示访客昵称、未读数、最后消息、接入时间）
- 右侧：选中访客的聊天区域，复用 `ChatBox.vue` 或在其基础上封装
- 顶部工具栏：显示"当前接待数 / 最大接待数"，在线状态切换按钮（P1）
- 访客卡片右键/操作按钮：结束会话

**路由注册**：在 `sys_menu` 中新增菜单，组件路径 `cs/workbench/index`

### 6.3 客服管理页面（管理员）

**文件位置**：`ruoyi-ui/src/views/cs/staff/index.vue`

**功能**：
- 从 `sys_user` 列表中选择用户，标记为客服 / 取消标记
- 表格展示：用户名、昵称、客服状态、最大接待数（P1）
- 遵循项目既有 CRUD 模式（`el-table` + `el-dialog` + `el-form`）

### 6.4 我的客服历史（已登录用户，新增）

**文件位置**：`ruoyi-ui/src/views/cs/myHistory/index.vue`（或挂在"个人中心"下作为子 tab）

**功能**：
- 用户登录后访问 → 调 `/cs/my/history`
- 列表展示该用户名下所有客服会话（包括登录前由 IP 关联到的匿名会话），含起止时间、对接客服、最后一条消息
- 点击展开消息明细 → 调 `/cs/my/history/{sessionId}/messages`
- 顶部提示语：登录瞬间触发的 IP 关联结果，可显示"我们识别到您此前在本网站咨询过 N 次，已为您归档"
- 该页面 **不能发起新会话**，仅作为只读历史查阅入口

### 6.5 会话记录查询（管理员，P1）

**文件位置**：`ruoyi-ui/src/views/cs/history/index.vue`

**功能**：
- 按时间范围、客服、访客查询历史会话
- 点击会话可查看完整消息记录

---

## 7. 实施路线图

### 阶段一：基础准备（1-2 天）
1. 数据库变更：执行 `sys_user`、`chat_session` 字段新增；创建 `chat_visitor` 表（含 `bound_user_id` / `last_cs_user_id` / `idx_ip`）
2. 后端实体、Mapper、Service 层搭建
3. 访客接入 API（`/cs/connect`）开发
4. 分配引擎实现：**上次客服优先 → 最小接待数 → 满员话术兜底（等待集合）**

### 阶段二：访客端（2-3 天）
1. 开发 `GuestChatWidget.vue` 组件（含"等待中"状态 UI）
2. WebSocket 访客匿名认证适配
3. 访客发送消息接口 `/cs/message/send`
4. 找一个现有 PC 页面嵌入测试

### 阶段三：客服端（2-3 天）
1. 开发客服工作台页面 `cs/workbench/index.vue`
2. 客服获取会话列表接口 `/cs/workbench/sessions`
3. 会话结束功能（含触发等待集合消费、回写 `last_cs_user_id`）
4. 客服发送消息接口（复用现有或独立接口）

### 阶段四：管理后台 + 登录关联（2-3 天）
1. 客服身份设置页面 `cs/staff/index.vue`
2. 后台菜单配置（插入 `sys_menu` 记录）
3. **登录钩子**：在用户登录成功的 Service 中调用 `bindByLogin(userId, ip)`，按 IP + 时间窗回填 `chat_visitor.bound_user_id`
4. **我的客服历史页**：`cs/myHistory/index.vue` + `/cs/my/history` 接口

### 阶段五：增强功能（P1，可选，2-3 天）
1. 客服状态切换（在线/忙碌/离线）
2. 最大接待数配置化（`cs_config.max_sessions`）
3. 访客信息展示（IP、来源页面、是否已登录）
4. 离线留言功能
5. 管理员会话记录查询页

> **明确不做**：客服绩效统计、排队 FIFO 队列、会话转接。

---

## 8. 关键设计决策（待用户确认）

### 8.1 访客身份与 WebSocket 认证
**选项 A（推荐，改动最小）**：访客不发真实 userId，WebSocket AUTH 时 `fromUserId=0`，额外携带 `visitorToken`；消息表 `from_user_id` 对访客存 `0`。
**选项 B（更规范）**：访客在 `chat_visitor` 表中创建记录后，分配一个独立的访客 ID（如从极大数起步），完全复用现有 userId 通道，但需避免与 `sys_user.id` 冲突。

### 8.2 客服状态与接待数存储
**选项 A（内存）**：在 `ConnectionManager` 中维护，实现最快，但仅支持单实例。
**选项 B（Redis）**：利用项目已有的 Redis，支持集群扩展。推荐此方案。

### 8.3 会话表复用 vs 新建
**选项 A（复用，推荐）**：客服会话继续使用 `chat_session` / `chat_message` 表，新增 `session_type=3` 和 `biz_type` 字段区分。好处是消息存储、历史查询、已读逻辑全部复用。
**选项 B（新建）**：创建独立的 `cs_session` / `cs_message` 表，逻辑完全隔离，但重复开发工作量大。

最终选型：选项B，新建独立表 `cs_session` / `cs_message`。

### 8.4 访客端技术形态
**选项 A（组件嵌入，推荐）**：将访客端做成 Vue 组件 `GuestChatWidget`，任何页面直接 `<GuestChatWidget />` 引入。
**选项 B（独立页面）**：做一个完全独立的 HTML 页面（不依赖 ruoyi-ui 的登录和布局），通过 iframe 或弹窗嵌入。适合完全隔离的场景。

最终选型：选项A，组件嵌入 `GuestChatWidget`。测试位置：首页登录页面右下角。

### 8.5 "上次客服优先"的边界处理
**选项 A（严格）**：上次客服离线时直接走默认分配，不等待。
**选项 B（宽松）**：若上次客服离线但接待数未满，把访客挂到该客服的"待接入"列表，等其上线主动接入；超时（如 30s）再走默认分配。

最终选型：选项B，宽松策略。客服离线但未满时挂起等待，超时30s转默认分配。

### 8.6 IP 关联历史的匹配策略
**选项 A（仅 IP，推荐 MVP）**：登录瞬间用当前 IP 在 90 天窗口内匹配，自动绑定。简单但存在共享 IP 误判可能。
**选项 B（IP + 用户确认）**：匹配到记录后弹窗"识别到您此前咨询过 N 次，是否归档到您账号？"，用户点击确认才绑定。**推荐用于隐私敏感场景**。
**选项 C（IP + UA 指纹）**：在选项 A 基础上叠加 user_agent 哈希校验，提高匹配准确度，对用户无感。

最终选型：选项B，IP + 用户确认。登录匹配到记录后弹窗由用户确认，确认后才绑定。

### 8.7 满员"等待集合"的存储
**选项 A（内存集合）**：直接用 ConcurrentHashSet 维护等待中的 visitor_token，单节点足够。
**选项 B（Redis Set）**：`cs:waiting` Set，支持多节点共享与崩溃恢复。

最终选型：选项B，基于Redis实现，与客服状态存储方案保持一致。

---

## 9. 风险与注意事项

1. **匿名用户安全**：访客接口（`/cs/connect`、`/cs/message/send`）需要防刷机制，建议对 visitor_token + IP 做限流（如每 5 秒最多发 3 条消息）。
2. **WebSocket 双端兼容**：客服通过现有后台 WebSocket 连接（端口 9999）收发消息；访客端也需连接同一 WebSocket 服务。需确保 `GUEST_AUTH` 或扩展的认证逻辑不会破坏现有私聊/群聊。
3. **客服下线处理**：客服异常断线（关闭浏览器、网络断开）时，需要心跳超时机制将其标记为离线，并释放接待数。已建立的会话是否立即结束？建议保留会话一定时间（如 5 分钟），客服重新上线后可恢复；超时后自动结束并提示访客。
4. **消息路由适配**：现有 `MessageRouter.routeMessage` 基于 `toUserId` 或 `sessionId` 查询成员推送。客服会话只有 1 个客服成员 + 访客（不在 member 表中），需要扩展路由逻辑：对 `session_type=3` 的会话，推送时同时推给客服和访客。
5. **"上次客服"已被取消客服身份**：分配前需校验 `last_cs_user_id` 对应的 `sys_user.is_customer_service=1`，否则直接走默认分配。
6. **IP 关联误判**：办公网/家庭网/校园网共享公网 IP 是常见情况，单纯按 IP 匹配可能把他人匿名记录绑到当前登录用户。**强烈建议**至少叠加时间窗（90 天）+ user_agent 弱指纹，且在管理后台保留"解绑历史会话"操作以便人工纠偏。
7. **IP 隐私合规**：`chat_visitor.ip` 属于个人信息，按 GDPR/个保法应：①登录绑定时记录用户授权日志；②提供"删除我的历史记录"自助入口；③日志/导出脱敏 IP（仅保留前两段）。
8. **等待集合并发**：满员兜底场景下，多个客服同时结束会话可能引发多个访客同时被分配；分配动作需在 Redis 上加 `SETNX` 锁或借助 Lua 脚本保证原子性，防止超额分配。
