# SchoolBedrockLink

用于 Paper 26.2 校园 Minecraft 服务器。

Bedrock 玩家通过学校现有 Blessing Skin OAuth2 完成身份认证，插件随后通过 Floodgate Local Linking 把 Xbox/Bedrock 身份绑定到该用户自己的 Java Profile UUID。无需修改现有皮肤站代码、Yggdrasil、authlib-injector 或 Java 登录流程。

## 依赖



- Paper/Purpur 26.2
- Java 25
- Geyser（当前最新版），auth-type: floodgate
- Floodgate（当前最新版）
- Floodgate SQLite Local Linking 扩展
- 一个已启用 OAuth2 的 Blessing Skin 实例
- Nginx HTTPS（公网认证网页）

插件使用 Blessing Skin 的现有接口：

~~~
OAuth Authorization:       /oauth/authorize
OAuth Token:               /oauth/token
当前 OAuth 用户:           /api/user
当前用户 Players:          /api/players
Yggdrasil Profile Lookup:  /api/yggdrasil/api/profiles/minecraft
~~~

以上地址均由配置提供；文中的 skin.example.edu 和 mc-auth.example.edu 都是占位域名，不代表实际部署。

## 身份和安全模型

~~~
Bedrock/Xbox
  -> Floodgate Bedrock UUID
  -> SchoolBedrockLink 绑定码
  -> Blessing Skin OAuth Authorization Code + PKCE S256
  -> GET /api/user 验证当前 OAuth 用户
  -> GET /api/players 取得该用户拥有的角色名
  -> POST /api/yggdrasil/api/profiles/minecraft 取得真实 Java UUID
  -> Floodgate PlayerLink
  -> Approved Link Registry
~~~

/api/players 返回的 name 是允许查询的唯一角色来源；pid、uid 不会被当成 UUID。Yggdrasil 返回的 Profile 还必须与该角色名集合相交，意外返回的 Admin 等角色会被丢弃。浏览器只能提交服务器内存 Session 中的 profileIndex，不能提交可信的 Java UUID。

Floodgate isLinked() 也不是学校认证凭据。插件必须同时确认 Floodgate 当前 Mapping 和 approved-links.json 完全一致，因此 Global Linking 不能绕过学校 OAuth。插件不读取、写入、迁移或复制 world/playerdata；Java 和 Bedrock 共享数据依靠相同的 Java UUID 自然实现。

## 快速部署

### 1. 准备 Geyser、Floodgate 和 Local Linking

安装与 Paper 26.2 匹配的当前 Geyser/Floodgate，并在 Geyser 中设置：

~~~
auth-type: floodgate
~~~

安装官方 Floodgate SQLite Local Linking 扩展，确认 Floodgate 配置中的本地 Linking 已启用。字段名以当前 Floodgate 版本为准，目标配置应等价于：

~~~
player-link:
  enabled: true
  require-link: false
  enable-own-linking: true
  allowed: false
  type: sqlite
  enable-global-linking: false
~~~

预期：Floodgate 能启动 SQLite Local Linking；普通玩家不能通过 /linkaccount 直接绕过学校 OAuth。失败先看 Floodgate 启动日志、Local Linking 扩展版本和实际配置字段。

### 2. 安装插件并准备 D 盘构建产物

将 build/libs/SchoolBedrockLink-0.1.0.jar 复制到服务器的 plugins/ 目录，使用 Java 25 启动一次。

预期生成：

~~~
plugins/SchoolBedrockLink/config.yml
plugins/SchoolBedrockLink/approved-links.json
plugins/SchoolBedrockLink/link-audit.jsonl
~~~

默认配置尚未填写 OAuth URL、Client ID 和 Secret，所以 /schoollink status 显示 NOT READY 是正常的。失败先查 Java 版本、Paper 日志和插件是否被 Paper 加载。

### 3. 在 Blessing Skin 后台创建 OAuth 应用

只在现有 Blessing Skin OAuth 管理后台操作，不修改源码、不增加皮肤站插件、不改数据库：

1. 创建名为 SchoolBedrockLink 的 OAuth 应用。
2. 复制后台实际生成的 Client ID。
3. 设置精确的 Redirect URI。生产环境例如：
   https://mc-auth.example.edu/oauth/callback
4. 只授予读取当前用户和读取当前用户 Players 所需的最小 Scope。

Blessing Skin 官方路由中，/api/user 使用 User.Read，/api/players 使用 Player.Read 或更高权限；实际 Scope 以已部署实例和 OAuth 应用后台为准，插件默认 scopes: []，不会硬编码 profile。

预期：普通学校账号可以授权，且插件能用该 Token 读取自己的 /api/players。若实例没有 OAuth Provider 或没有这些现有 API，当前方案缺少必须的身份接口；不要通过修改插件或皮肤站源码绕过它。

### 4. 填写插件配置

编辑 plugins/SchoolBedrockLink/config.yml。默认文件不会预填任何皮肤站域名，请把下面的占位值换成你的 Blessing Skin 实例地址。以下 https://skin.example.edu 仅为占位示例：

~~~
http:
  bind-address: "127.0.0.1"
  port: 8787
  public-base-url: "https://mc-auth.example.edu"
  request-timeout-seconds: 10

development-mode: false

oauth:
  mode: "blessing-skin"
  authorization-url: "https://skin.example.edu/oauth/authorize"
  token-url: "https://skin.example.edu/oauth/token"
  user-endpoint: "https://skin.example.edu/api/user"
  players-endpoint: "https://skin.example.edu/api/players"
  yggdrasil-profiles-endpoint: "https://skin.example.edu/api/yggdrasil/api/profiles/minecraft"
  client-id: "填写 OAuth 后台实际生成的 Client ID"
  client-secret: "${ENV:SCHOOL_BEDROCK_OAUTH_SECRET}"
  scopes: []
  pkce: true
~~~

生产模式要求所有远程接口使用 HTTPS，public-base-url 必须带协议，并且必须与 OAuth 后台的 Redirect URI 完全一致。根据后台实际要求填写最小 Scope，例如：

~~~
scopes:
  - "User.Read"
  - "Player.Read"
~~~

如果已部署 Provider 不需要显式 Scope，保持 scopes: []。不要写 scope: null，也不要为了绕过 Provider 要求硬编码 profile。

在启动 Minecraft 的同一 Windows 系统账号环境中设置 Secret：

~~~powershell
$env:SCHOOL_BEDROCK_OAUTH_SECRET="真实 Client Secret"
~~~

不要把真实 Secret 写入配置文件、README、Git、Audit 或日志。client-id: CHANGE_ME 或环境变量缺失时，插件保持 NOT READY。

### 5. 本地测试

本地测试可以保持默认：

~~~
http:
  bind-address: "127.0.0.1"
  port: 8787
  public-base-url: "http://127.0.0.1:8787"
  request-timeout-seconds: 10

development-mode: true
~~~

在 Blessing Skin OAuth 后台把 Redirect URI 精确设置为：

~~~
http://127.0.0.1:8787/oauth/callback
~~~

development-mode: true 只允许 HTTP 的 localhost/loopback 地址，生产公网地址仍应使用 HTTPS。若本机无法回调，先检查端口占用、Secret、Client ID 和 Redirect URI 是否逐字一致。

### 6. 配置 Nginx（生产）

参考 docs/nginx.example.conf，将公网 HTTPS 域名反代到 127.0.0.1:8787：

~~~nginx
location / {
    proxy_pass http://127.0.0.1:8787;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-For $remote_addr;
    access_log off;
}
~~~

DNS、证书、public-base-url 和 OAuth Redirect URI 必须使用同一个地址。**不要把 8787 端口开放到公网。** 插件只接受 loopback 连接中的单个 IP 字面量 X-Forwarded-For；Nginx 必须用 $remote_addr 覆盖客户端值，禁止 $proxy_add_x_forwarded_for。多层代理/CDN 需要明确配置可信网段，不能直接套用此示例。

### 7. 检查状态并端到端测试

执行：

~~~
/schoollink status
~~~

预期 READY，并且 Floodgate、PlayerLink、OAuth、HTTP Server、Approved Links 均正常。然后使用 Bedrock 测试账号：

1. 第一次进入服务器，看到一次性绑定码并被拒绝进入世界。
2. 打开认证页面，输入绑定码，跳转 Blessing Skin 并正常登录授权。
3. 插件调用 /api/user、/api/players 和 Yggdrasil Profile API。
4. 只选择当前账号拥有的角色，确认绑定。
5. 重新连接 Bedrock；然后用同一个 Java Profile 登录，验证物品、经验和位置相同。

完整人工验收见 docs/acceptance.md。

## HTTP 接口

插件内置 JDK HttpServer，默认只监听 127.0.0.1:8787：

~~~
GET  /                  绑定首页
GET  /start?code=...    验证绑定码并开始 OAuth
GET  /oauth/callback    消费一次性 state，读取 Blessing Skin 用户和角色
POST /confirm           只接受 confirmationToken + profileIndex
GET  /healthz           返回组件布尔状态，不返回用户、Token 或 Secret
~~~

页面使用 no-store、CSP、禁止 iframe、HTML escape 和内存限流。默认每 IP 每分钟 30 次，同一绑定码每 5 分钟最多开始 5 次 OAuth。

## 管理命令

需要 schoolbedrocklink.admin（默认 OP）：

~~~
/schoollink status
/schoollink status <在线玩家名|Java-UUID|Bedrock-UUID|XUID>
/schoollink unlink <在线玩家名|Java-UUID|Bedrock-UUID|XUID>
/schoollink reload
~~~

reload 直接读取服务器 plugins/SchoolBedrockLink/config.yml，验证成功后发布不可变配置；读取、YAML、有效值或 OAuth 就绪校验失败时保留旧配置。OAuth、绑定、消息和限流可重载；监听地址、端口、public-base-url、请求超时和 development-mode 必须重启。已开始的 OAuth 使用原配置快照完成；认证码长度、有效期或复用设置变化会使旧码和相关会话失效。unlink 通过官方 PlayerLink 验证后删除 Approved Record 并写 Audit。支持明确标识 java:<UUID>、bedrock:<UUID>、xuid:<XUID>；歧义标识拒绝操作。

## 文件与故障安全

- approved-links.json 只保存 Bedrock UUID、XUID、Gamertag、Java UUID、Java Username 和绑定时间。
- Registry 使用临时文件、flush、atomic move，并保留 .bak。损坏时不会覆盖原文件或自动变成空 Registry；Bedrock fail closed，Java 不受插件判断影响。
- link-audit.jsonl 记录 BIND、UNLINK、REPAIR、CONFLICT 的必要字段，不记录密码、Access/Refresh Token、Authorization Code、Cookie、PKCE verifier、OAuth state 或 Confirmation Token。
- 未认证或冲突的 Bedrock 玩家不能因为 Floodgate isLinked() 而放行。
- Pending code、OAuth state、PKCE verifier、Access Token 和确认 Session 只在内存短期存在；Access Token 不持久化。

## 构建

只支持 Java 25。Gradle Wrapper 的缓存和临时目录默认在项目 D 盘目录：

~~~
.gradle-user-home
.gradle-tmp
~~~

执行：

~~~powershell
.\gradlew.bat --gradle-user-home "D:\Code\SchoolBedrockLink\.gradle-user-home" --no-daemon --console=plain clean test build
~~~

产物：

~~~
build/libs/SchoolBedrockLink-0.1.0.jar
~~~

## 官方资料

- [Paper 项目配置](https://docs.papermc.io/paper/dev/project-setup/)
- [Floodgate API](https://geysermc.org/wiki/floodgate/api/)
- [Floodgate Linking](https://geysermc.org/wiki/floodgate/linking/)
- [Floodgate API Javadocs](https://repo.opencollab.dev/javadoc/maven-snapshots/org/geysermc/floodgate/api/latest/)
- [Blessing Skin 官方 API 路由](https://raw.githubusercontent.com/bs-community/blessing-skin-server/dev/routes/api.php)
- [Blessing Skin Players API 源码](https://raw.githubusercontent.com/bs-community/blessing-skin-server/dev/app/Http/Controllers/PlayerController.php)
- [Blessing Skin Yggdrasil Profile API 源码](https://github.com/bs-community/blessing-skin-plugins/blob/master/plugins/yggdrasil-api/src/Controllers/ProfileController.php)

## 多设备部署与跳转排查

http.bind-address 是服务器内部监听地址，http.public-base-url 是玩家浏览器访问地址。服务器本机可用的 127.0.0.1/localhost 指向玩家自己的设备，不能发给其他玩家。生产使用可达的 HTTPS 域名；开发 HTTP loopback 仅用于同机测试。游戏中显示的地址、反代入口和 OAuth 应用配置需要根据真实环境核对。

public-base-url 可以是 https://mc-auth.example.edu/auth。首页 /auth/、表单 /auth/start 与 /auth/confirm、回调 /auth/oauth/callback 会保持前缀。反代必须保留此路径，不能用 proxy_pass 的尾随 / 去除前缀；前缀仅允许字母、数字、下划线和短横线组成的路径段。OAuth 后台注册的 redirect_uri 必须与 public-base-url 去除尾部斜杠后加 /oauth/callback 逐字一致，包含协议、域名、端口和大小写。

CSP 的 form-action 同时允许 self 与配置 authorization-url 的精确 origin；首页及 302 响应均设置此策略。Provider 在同一 origin 的 /authorize → /login 跳转可通过。若实际登录链还跳到其他 origin，当前策略会继续阻止；需要先提供脱敏后的 origin 链，再审核所需来源，不能增加通配符。OAuth 改域名后请重新打开首页，避免使用旧页面中的 CSP。

排查顺序：
1. 第二台设备（最好切换移动网络）打开游戏显示的完整地址。首页不可达先查 DNS、证书、防火墙、反代和公网可达性。
2. 在 Chromium 中输入新认证码并点击开始认证，观察是否得到 302，以及浏览器是否到达皮肤站登录页。只有 curl 收到 302 不足以证明浏览器通过 CSP。
3. /start 的 400 表示码无效/过期，429 表示限流，503 表示配置未就绪或繁忙。默认同一码五分钟最多启动五次，限流不删除认证码。
4. 浏览器控制台若提示 form-action，记录脱敏的来源链；Provider 拒绝授权时核对 redirect_uri、Client ID 和 Scope；回调错误查看页面错误编号与服务器对应的阶段、分类、status、upstreamStatus。
5. 授权后选择自己拥有的角色并确认；重新进入 Bedrock，再核对真实 Floodgate Mapping 和 Approved Registry。

300 秒从认证码首次创建开始；重连复用旧码不重新计时。OAuth 与确认会话使用同一配置时长，但最终确认还要求原认证码未过期，因此不是每一步都额外延长 300 秒。

上游响应读取过程中限制为 1 MiB，并对整个响应（含响应体）设置超时和取消。HTTP 保留四个工作线程、32 个排队名额；最多两个 callback/confirm 远程操作并行，超额请求返回 503 + Retry-After，繁忙拒绝发生在消费 state 前。极端连接洪峰超过队列容量仍可能关闭连接，需要 Nginx 连接限制配合。

诊断仅记录关联编号、固定阶段/分类、HTTP 状态和异常类型；不记录 secret、token、code、state、verifier、Cookie 或带查询参数的完整 URL。请关闭/脱敏此认证路径的反代访问日志；不要把 HAR 原件或完整回调 URL 发到工单。

## 本地回归与部署生效

强制执行测试（不使用缓存）：
~~~powershell
.\gradlew.bat --gradle-user-home ".gradle-user-home" --no-daemon --console=plain clean test build --no-build-cache --rerun-tasks
~~~

真实浏览器本地模拟链路：
~~~powershell
.\gradlew.bat --gradle-user-home ".gradle-user-home" --no-daemon --console=plain browserFixture
~~~
访问控制台输出的 loopback 首页，输入仅供该测试的码，点击模拟 Provider 授权并完成确认。模拟器使用生产 HTML/HTTP handler，Provider/Floodgate 为本地替身，不能替代线上验收。结束后 Ctrl+C。测试产物不包含在插件 JAR 中。

将 build/libs/SchoolBedrockLink-0.1.0.jar 替换到目标服务器（避免同时保留旧版 JAR），重启 Paper/Purpur 使代码生效；单纯 /schoollink reload 不加载新 JAR。保留服务器现有 config.yml、approved-links.json 和备份。按 docs/nginx.example.conf 审核反代，先 nginx -t 再重载。最后按 docs/acceptance.md 在第二台设备验收。

若本地通过但线上仍失败，只需提供脱敏的 public-base-url、authorization-url 和实际跳转 origin 链、OAuth 后台 Redirect URI、浏览器及失败阶段、页面错误编号和对应安全日志，以及服务器是否已重启加载新 JAR；不要提供 Client Secret、Token 或完整回调查询串。
