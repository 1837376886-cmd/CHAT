# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

本项目基于 **若依（RuoYi）Vue 分离版 3.9.0**，在原有后台管理系统基础上扩展了**在线客服（客服）聊天模块**。目前正在进行"客服系统独立化改造"，改造方案详见仓库根目录的 `客服系统独立化改造方案.md`。

改造目标是让客服系统能够独立部署：
- 本地主库存储客服业务数据（`cs_*`、`chat_*` 等表）
- 从库读取另一套若依系统的系统数据（`sys_user`、`sys_role`、`sys_menu` 等）
- 拥有独立的登录态，Token 不共享
- 仅允许客服 / 管理员 / 开发角色登录

**当前状态**：改造方案文档已完成，但代码中尚未应用多数据源配置和登录角色限制。

## 技术栈

- **后端**：Java 1.8、Spring Boot 2.5.15、Spring Security 5.7.12、MyBatis + MyBatis-Plus 3.5.3.1、Netty 4.2.4、Redis、Druid、JWT、Swagger 3、PageHelper
- **前端**：Vue 2.6.12、Element UI 2.15.14、Vue CLI 4.4.6、Axios、ECharts 5.4.0
- **数据库**：MySQL（配置在 `ruoyi-admin/src/main/resources/application-druid.yml`）

## 模块结构

| 模块 | 职责 |
|------|------|
| `ruoyi-admin` | Spring Boot 启动入口（`RuoYiApplication`），HTTP 端口 8080 |
| `ruoyi-framework` | 框架配置：安全、数据源、MyBatis、AOP、登录服务、JWT 过滤器 |
| `ruoyi-system` | 系统管理：用户、角色、菜单、部门、字典。改造完成后应从从库读取 |
| `ruoyi-common` | 公共工具类、常量、基础领域类 |
| `ruoyi-chat` | 客服业务：Netty WebSocket 服务器（端口 9999）、会话、消息、访客、客服配置、转接 |
| `ruoyi-generator` | 代码生成（改造方案建议移除） |
| `ruoyi-quartz` | 定时任务（改造方案建议移除） |
| `ruoyi-ui` | Vue 前端，开发服务器端口 80，代理 `/dev-api` 到 `localhost:8080`，代理 `/ws` 到 `ws://localhost:9999` |

## 常用命令

### 后端

```bash
# 编译整个项目
mvn clean compile

# 运行应用（从项目根目录）
mvn spring-boot:run -pl ruoyi-admin

# 或在 IDE 中直接运行 RuoYiApplication
# 位置：ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java

# 生产打包
mvn clean package -DskipTests
```

### 前端

```bash
cd ruoyi-ui

# 安装依赖（仓库中已有 pnpm lockfile）
npm install

# 启动开发服务器（端口 80，代理到 localhost:8080）
npm run dev

# 生产构建
npm run build:prod

# 测试环境构建
npm run build:stage
```

**本代码库中没有单元测试。**

## 高层架构

### 双服务器运行时

应用同时运行**两个独立的网络服务器**：

1. **Spring Boot Tomcat**（端口 8080）：处理所有 HTTP 请求的标准 REST API 服务器。
2. **Netty WebSocket 服务器**（端口 9999）：独立的 WebSocket 服务器，用于实时聊天消息推送。

Netty 在 `NettyWebSocketServer.java` 中通过 `@PostConstruct` 在独立线程中启动。前端开发代理（`vue.config.js`）将 `/ws` 路由到 `ws://localhost:9999`。注意，这**不是** Spring 自带的 WebSocket，而是基于 `WebSocketServerProtocolHandler` 的原始 Netty 启动器。

关键文件：
- `ruoyi-chat/src/main/java/com/ruoyi/chat/netty/NettyWebSocketServer.java`
- `ruoyi-chat/src/main/java/com/ruoyi/chat/netty/ChatChannelHandler.java`
- `ruoyi-ui/vue.config.js`（代理配置）

### MyBatis + MyBatis-Plus 混合使用

原有若依模块（`ruoyi-system`、`ruoyi-framework`）使用**原生 MyBatis** 配合 XML Mapper。`ruoyi-chat` 模块引入了 **MyBatis-Plus 3.5.3.1**，使用以下特性：
- `QueryWrapper` / `LambdaQueryWrapper` 做动态查询
- `BaseMapper`、`IService`、`ServiceImpl` 做 CRUD
- `Page<T>` 做分页
- `MybatisPlusInterceptor` 配合 `PaginationInnerInterceptor`（注册在 `MyBatisConfig.java`）

`ruoyi-framework` 的 `MyBatisConfig` 使用 `MybatisSqlSessionFactoryBean`（而非 `SqlSessionFactoryBean`），以便在同一个工厂中同时支持 MyBatis-Plus 和原生 XML Mapper。

### 客服权限模型

标准若依的安全模型中没有专门的"客服"角色权限。系统改为使用用户记录上的**布尔标志位**：

- `sys_user.is_customer_service`（0 = 否，1 = 是）
- 由 `sql/cs_init.sql` 添加

`CustomerServiceController` 中几乎所有客服接口都会显式校验该标志：
```java
if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
    return AjaxResult.error("无权访问");
}
```

客服人员设置接口（`/cs/staff/set`）用于切换该标志。`CustomerServiceAllocationService` 在分配访客时，只将 `is_customer_service = 1` 的用户视为可用客服。

### 以 Redis 为核心的运行时状态

由于 Netty 运行在 Spring HTTP 请求生命周期之外，实时聊天状态全部保存在 **Redis** 中（而非内存）：

- **在线状态**：`cs:online:{userId}` — 客服是否在线
- **当前接待数**：`cs:active:{userId}` — 该客服当前打开的会话数
- **最大接待数**：`cs:max:{userId}` — 配置的最大并发接待数
- **等待队列**：Sorted set，存储等待分配的访客
- **访客会话绑定**：`visitor:session:{token}` — 访客 token 映射到当前 `cs_session.id`
- **转接请求**：临时的 Redis 条目，用于客服之间的会话转接

管理类：`CustomerServiceRedisManager`（`ruoyi-chat` 模块）。

### 访客生命周期与分配

1. 访客调用 `POST /cs/connect`（无需登录）。系统创建或查询 `chat_visitor` 记录。
2. `CustomerServiceAllocationService.allocate(visitor)` 决定分配策略：
   - 如果该访客的 `last_cs_user_id` 在线且未满员 → 优先分配给该客服（粘性分配）
   - 否则如果有其他在线客服有空余容量 → 分配给第一个可用客服
   - 否则 → 将访客放入 Redis 等待队列，返回 `waiting: true`
3. WebSocket 消息按 `visitorToken`（访客端）或 `userId`（客服端）路由。
4. 客服上线（`POST /cs/online`）时，系统消费等待队列。
5. 会话结束或客服下线时，关闭所有进行中的会话，并重新消费等待队列。

### 会话转接流程

客服可以将进行中的会话转接给另一位在线客服：
1. `POST /cs/transfer/request` — 在 Redis 中创建转接请求，通过 WebSocket 推送给目标客服
2. 目标客服收到 `MessageType.CS_TRANSFER_REQUEST` WebSocket 消息
3. 目标客服通过 `POST /cs/transfer/accept` 或 `/reject` 响应
4. 接受后：关闭旧会话，以目标客服创建新会话，发送系统消息和 WebSocket 通知给原客服、目标客服、访客三方

## 数据库与 SQL

SQL 文件位于 `sql/` 目录：
- `cs_init.sql` — 客服相关表（`chat_visitor`、`cs_session`、`cs_message`、`cs_config`、`cs_visitor_tag`）及菜单初始化数据
- `ry_20250522.sql` — 若依基础数据
- `quartz.sql` — Quartz 定时任务表

当前数据源配置：`ruoyi-admin/src/main/resources/application-druid.yml`
- 单数据源，指向 `ruoyi-chat` 数据库
- 改造方案计划改用 `dynamic-datasource-spring-boot-starter` 实现 `master` + `slave` 多数据源，但**尚未实施**

## 关键配置

- **应用配置**：`ruoyi-admin/src/main/resources/application.yml`
  - HTTP 端口：8080
  - Netty WS 端口：9999（`chat.netty.port`）
  - Token 有效期：30 分钟
  - 文件上传路径被硬编码为 macOS 路径：`/Users/fanzijian/WorkSpace/RuoYi-Chat/temp/upload`

- **Druid 监控**：`/druid/index.html`（账号：ruoyi / 123456）
- **Swagger UI**：`/swagger-ui/index.html`（开发环境开启，前缀 `/dev-api`）

## 前端结构

客服页面位于 `ruoyi-ui/src/views/cs/`：
- `workbench/index.vue` — 客服工作台（会话列表、聊天面板、访客信息）
- `staff/index.vue` — 客服人员设置（设置/取消客服身份、配置最大接待数、自动回复语）
- `myHistory/index.vue` — 我的客服历史（客服视角的接待记录）

前端 API 封装在 `ruoyi-ui/src/api/cs.js`。

标准若依的系统管理页面（`views/system/`、`views/tool/`、`views/monitor/`）仍然存在，但改造方案建议删除，因为独立后的客服系统不应直接操作系统数据。

## 开发注意事项

- **代码库中没有测试**。所有验证必须通过手动测试或运行应用完成。
- `RuoYiApplication` 的 `@SpringBootApplication` 显式排除了 `DataSourceAutoConfiguration.class`，因为若依通过 `DruidConfig` / `MyBatisConfig` 手动配置数据源。
- `ruoyi-chat` 依赖 `ruoyi-system` 的 `ISysUserService`，若两模块继续膨胀需注意循环依赖风险。当前依赖关系为 `ruoyi-chat → ruoyi-system → ruoyi-common`。
- `ruoyi-chat` 使用了 Lombok，老模块中基本未使用。
- `application.yml` 中的文件上传路径被硬编码为某位开发者的 macOS 本机路径，在其他环境运行时需要修改。
