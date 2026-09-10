# SchoolBedrockLink 人工验收清单

这份清单验证的是“Bedrock 通过现有 Blessing Skin OAuth 后使用该用户的 Java Profile UUID”，不是数据复制功能。本文中的认证域名使用配置文件中的 public-base-url；不要把示例域名当成插件默认值。

## 前置检查

1. 用 Java 25 启动 Paper/Purpur 26.2。
2. Geyser 设置 auth-type: floodgate。
3. Floodgate 已安装 SQLite Local Linking 扩展，且本地 Linking 已启用；禁止用户绕过认证使用 /linkaccount，关闭 Global Linking。
4. SCHOOL_BEDROCK_OAUTH_SECRET 已在 Minecraft 服务进程环境中设置。
5. 生产环境 Nginx HTTPS 域名与 http.public-base-url、OAuth Client Redirect URI 完全一致；本地测试时可使用 development-mode: true 和 http://127.0.0.1:8787。
6. config.yml 已填写 Blessing Skin 实例的以下地址：
   - /oauth/authorize
   - /oauth/token
   - /api/user
   - /api/players
   - /api/yggdrasil/api/profiles/minecraft
7. OAuth Client ID 是 Blessing Skin 后台实际生成的值，pkce: true。scopes 按站点实际安装的 OAuth 实现填写：
   - Blessing Skin 自带 OAuth2（Passport）：`["User.Read", "Player.Read"]`，两个都要写。只写 `Player.Read` 不会自动附带 User.Read（默认 scope 仅在请求完全不带 scope 时生效），会导致 /api/user 失败。
   - 站点安装了 Yggdrasil Connect 插件时：该插件会在 /oauth/authorize 上拦截请求，只要 scope 里出现任何 `Yggdrasil.*`（以及 profile/email）而**没有 `openid`**，就直接以 `error=invalid_scope` 拒绝。此时应写 `["openid", "Yggdrasil.PlayerProfiles.Read"]`，并把 user-endpoint / players-endpoint 指向 `<site>/yggc/userinfo`（该接口返回 `availableProfiles: [{"id": "<uuid>", "name": "<角色名>"}]`，插件已支持该结构）。
   - 不确定时以 Provider 的 discovery 文档为准（`<issuer>/.well-known/openid-configuration` 的 scopes_supported）。
8. 执行 /schoollink status，预期 READY，Floodgate、PlayerLink、OAuth、HTTP Server、Approved Links 均正常。

如果前置检查失败：先看 /schoollink status 和服务器控制台；不要打开公网 8787，不要删除 approved-links.json 或 .bak，不要直接改 Floodgate SQLite。

## Java 玩家不受影响

1. 用学校皮肤站现有 Java 登录流程进入服务器。
2. 预期：进入、聊天、背包和既有 Yggdrasil/authlib-injector 行为与安装插件前相同。
3. 预期：插件不会为 Java 玩家创建 Pending code，也不会跳转 OAuth。

失败先查：authlib-injector/Yggdrasil 原有日志、Paper 其他插件；确认玩家不是通过 Geyser/Floodgate 连接。

## 未认证 Bedrock

1. 使用没有 Approved Record 的 Bedrock/Xbox 测试账号进入服务器。
2. 预期：AsyncPlayerPreLoginEvent 拒绝本次连接，Kick 显示配置长度的认证码与剩余分钟数（默认 300 秒）；玩家不能进入世界。
3. 未过期时断开再进入，预期：同一个 XUID 复用原码，截止时间不后移。300 秒的码在创建后 59 秒仍有效、300 秒时失效。
4. 如果 Floodgate 显示已有 Global Link，但 Registry 没有记录，预期仍然拒绝并要求学校 OAuth。
5. 若 Registry 损坏或 Floodgate/PlayerLink 不可用，预期 Bedrock fail closed，Java 仍可按原流程登录。

失败先查：Floodgate API、approved-links.json 健康状态、HTTP Server 状态；不要用 /linkaccount 作为测试方案。

## Blessing Skin OAuth、PKCE 和角色

1. 打开配置中的 public-base-url，输入游戏内认证码并提交。
2. 预期：/start 只在 code 有效时跳转配置的 Blessing Skin authorization-url；授权请求包含 response_type=code、state、code_challenge 和 code_challenge_method=S256。
3. 在 Blessing Skin 正常登录并授权，预期返回配置中的 /oauth/callback。
4. 预期：插件用一次性 Authorization Code 换取短期 Access Token，然后：
   - 带 Bearer Token GET /api/user 验证 OAuth 用户；
   - 带 Bearer Token GET /api/players 获取当前用户角色名；
   - 将这些角色名作为 JSON 数组 POST 到 Yggdrasil profiles endpoint。
5. Access Token 只在内存中短暂使用，不写文件、Audit 或日志；HTTP Header 不会被 dump。
6. /api/players 只有 PlayerA 时，即使 Yggdrasil 返回 PlayerA 和 Admin，页面也只能显示 PlayerA。
7. /api/players 返回空数组时，显示“当前皮肤站账号没有可用的 Minecraft 游戏角色”，不执行 Floodgate Binding。
8. Yggdrasil 返回空数组或没有任何允许角色的有效 UUID 时，显示“无法从学校 Yggdrasil 获取角色 UUID”，不产生绑定。
9. 试图向 Yggdrasil 请求一个不在 /api/players 中的 Admin 名称，插件不会主动发送该名称。
10. 修改浏览器 HTML，提交 javaUuid=管理员 UUID 或越界 profileIndex，预期拒绝；服务器只按自己的 Session 使用 profileIndex。
11. 重放同一个 OAuth callback state，预期第二次被拒绝；等待过期 state，预期被拒绝。
12. 关闭或调整 OAuth Provider 的 Scope 做失败测试，预期 /api/user 401 显示“皮肤站登录授权已经失效”，/api/players 401/403 显示“无法读取你的 Minecraft 角色”。

失败先查：Blessing Skin OAuth Client 的 Redirect URI、Client ID/Secret、Scope、五个 URL、HTTP 状态码和服务器控制台；不要猜接口格式，也不要把 Token 粘贴到日志或工单。

## 确认绑定与一对一

1. 在确认页核对 Xbox Gamertag 和 Java Profile 名称；UUID 即使显示也只作信息。
2. 点击确认后，预期页面只提交 confirmationToken + profileIndex。
3. 预期执行顺序是：验证短期 Session -> 检查一对一冲突 -> 调用官方 Floodgate PlayerLink -> 再查询 Mapping -> Mapping 等于目标 Java UUID 后写 approved-links.json -> 写 Audit。
4. 绑定成功页提示关闭页面并重新连接；刷新/重发 Confirmation Token 不会重复执行。
5. 用另一个 Bedrock 身份选择同一个 Java Profile，预期冲突并拒绝。
6. 同一个 Bedrock/Java 对再次提交，预期幂等成功。

失败先查：Floodgate PlayerLink 实现名称、SQLite 扩展版本和 Floodgate 日志；检查 Registry 是否仍有 .bak。冲突时插件不会自动覆盖已有 Mapping。

## 重新连接、自动修复和冲突

1. 绑定成功后 Bedrock 再次进入。
2. 预期：Floodgate Mapping 与 Approved Record 都指向同一个 Java UUID，直接放行。
3. 在测试环境删除/丢失该 Floodgate Mapping，重新进入。
4. 若 auto-repair-missing-floodgate-link: true，预期插件调用官方 PlayerLink 自动修复，然后拒绝当前连接并提示“账号绑定已经自动修复，请重新连接服务器”；第二次连接放行。
5. 将 Mapping 改成另一个 Java UUID，重新进入。
6. 预期：Kick 为冲突，记录 CONFLICT，插件不自动覆盖、不放行。
7. 验证只有 Approved Record 而没有 Floodgate Mapping 时不会直接放行；只有 Floodgate Global Mapping 而没有 Approved Record 时也不会放行。

## 共享玩家数据最终验收

1. 用已绑定 Bedrock 账号进入，拿一个物品、获得经验并移动到明显位置，然后退出。
2. 用同一个学校 Java Profile 登录。
3. 预期：看到同一个物品、经验和位置，以及由相同 Java UUID 识别的 LuckPerms、经济、领地、封禁等数据。
4. 检查插件源码和日志：没有读取、写入、迁移或复制 world/playerdata。

## 解绑和恢复

1. 对在线已绑定玩家执行 /schoollink unlink <player>。
2. 预期：先调用官方 unlinkPlayer(javaUuid) 并确认 Mapping 消失，再删除 Approved Record，写入 UNLINK SUCCESS Audit。
3. 破坏 approved-links.json 做恢复演练。
4. 预期：Bedrock 全部 fail closed，文件原文不被覆盖，日志为 SEVERE；Java 登录仍不被本插件判断影响。
5. 从管理员备份中人工恢复有效 Registry 后重启/重新加载，再重复状态检查。

## 本次修复的多设备验收

1. 确认目标服务器已经替换新 JAR 并重启，记录构建文件 SHA-256；仓库里的 plugins/ 并不等于真实服务器目录。
2. 使用第二台设备，随后切换到移动网络，打开游戏实际显示的认证地址。不能使用 localhost/127.0.0.1；记录首页 HTTP 状态，核实 HTTPS 证书与 DNS。
3. Chromium 开发者工具中保留网络记录，输入新认证码点击开始。预期 /start 302 后实际到达 Provider 登录页；记录 origin 链（不记录查询串）。控制台不得有 CSP form-action 拦截。若 Provider 跳转到额外登录域名，先审核来源链，禁止用 CSP 通配符修补。
4. 核对后台 Redirect URI 与实际 redirect_uri 逐字一致；若 public-base-url 为 https://mc-auth.example.edu/auth，所有表单和回调都应保留 /auth，Nginx 不剥离前缀。
5. 完成授权、角色选择、确认绑定，再用 Bedrock 重连，检查真实 Mapping、Approved Record、Java 登录和共享物品数据。刷新回调必须被拒绝；过期 state 不得触发 token API。
6. OAuth 拒绝授权、token API 401/500、角色 API 403、超大响应、慢响应分别测试。错误提示包含必要关联编号；安全日志包含阶段、类别和 HTTP 状态，不得含 secret/token/code/state/verifier/Cookie 或完整 URL。
7. 同一码快速开始六次（默认限流）：第六次应为 429“请求过于频繁”，而非“认证码过期”。在途会话仍遵守原截止时间。

## 重载、代理和离线管理

1. 在测试服实际编辑磁盘 config.yml 后执行 /schoollink reload。修改有效期为 900 秒后，新认证码采用新值；认证码参数变化会使旧码失效。再写入非法 YAML、非法范围或缺失 OAuth 必填值，重载必须失败且保留上次有效配置。完成后恢复管理员原值。
2. OAuth Client/端点重载前先开始授权，重载后继续回调：旧会话应始终使用旧快照，新会话使用新配置。切换授权 origin 后重新打开首页，避免旧 CSP。
3. 修改监听地址、端口、public-base-url、请求超时或 development-mode，命令应说明需要重启；重启前仍使用原设置。
4. 通过 Nginx 发送带伪造 X-Forwarded-For 的请求，Nginx 应以 $remote_addr 覆盖，不能绕过限流。多值/逗号链和非 IP 值不得作为插件身份；直连非 loopback 来源的转发头不可信。记录可信代理部署边界。
5. 将已绑定玩家下线，用 /schoollink status java:<UUID>、bedrock:<UUID>、xuid:<XUID> 查询，使用相同标识 unlink。管理员权限仍必需；先核对 Mapping 消失再删除记录并写 UNLINK SUCCESS。冲突、不可用、失败或过时记录均不得错误删除 Approved Record。
6. 同时模拟两个慢 callback，第三个应迅速 503 + Retry-After，首页仍响应；被繁忙拒绝的 state 没有被消费，可以稍后重试。极端连接洪峰超过 32 队列可能关闭连接，需要反代限连接配合。

本地 Chromium 模拟通过只能验证插件链路。Firefox 对照、真实皮肤站、多网络 DNS/TLS、实际 OAuth 注册信息和 Floodgate SQLite 扩展仍须部署环境验证。向开发者反馈仅提供脱敏地址、origin 链、失败阶段、错误编号和构建信息，不提供认证凭据或 HAR 原件。
