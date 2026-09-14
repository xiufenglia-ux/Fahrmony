# Fahrmony 个人测试版

基于 `xiufenglia-ux/Fahrmony` 的 `9e0109abfbb0cfb087a23b7704cacdcf75a6e8e5`，修改日期 2026-09-14。保留原作者 Tun & PaMa AG 的版权与 CC BY-NC 4.0 许可，仅供个人非商业用途。

## 1.1.8 开源研究落地

阅读 Nevolution 微信插件源码后，发现此前漏读旧式 CarExtender 的真实回复能力。本版补齐此路径，不需要安装该插件或替身 APK。使用原应用 UID 校验、可写凭据检查及输入键校验；无能力时不制造假回复。研究比较见 `OPEN_SOURCE_REVIEW.md`。

三项新增 Android 设备端回归测试覆盖旧式回复提取、无接口、不可写凭据。测试 APK 编译与设备执行是不同的检查；本次没有手机/模拟器执行结果，不能把编译成功当成通过真机测试。

## 能力与限制

- 安装在手机，通过 Android Auto 使用，不是安装在车机的独立微信客户端。
- 可转发微信发布的文字通知，Android Auto 决定卡片显示及语音朗读。微信关闭通知正文、系统不给通知使用权或车机屏蔽通知时无法获取消息。
- 聊天中的语音消息只能显示通知中的摘要，不能读取或播放微信私有语音文件。
- 新增微信通话通知识别和本地操作转交。只接受微信通知中原有且由同一个微信 UID 创建的接听、拒接、挂断、回拨 PendingIntent。未提供操作时仅提醒，不生成虚假按钮。
- 普通消息只在原通知、Wearable 扩展或旧式 CarExtender.UnreadConversation 提供唯一、同应用 UID、可写的 RemoteInput 操作时显示真实回复入口。转交原应用不等于消息送达；没有接口时不再提供虚假回复动作。Android Auto 的标准消息集成要求回复动作，故无接口时车机的展示、朗读及弹窗都可能受限，甚至不显示，不能保证保持 1.1.6 的展示效果。
- 通话只在识别到真实操作时提供本地指令入口；Android Auto 可能仍将它显示为“回复”。它是通话控制，不发送聊天消息。没有操作时只尝试显示提醒。
- 同时读取标准 CallStyle extras 中的操作；保持原应用 UID 校验，已识别的视频不提供接听/回拨。
- 已读只关闭 Fahrmony 提醒，保留操作凭据至原通知撤回、更新或超时；不标记微信消息已读。
- 新消息或源消息时间变化允许再次提醒，重复内容与时间的更新保持静默。高优先级渠道和通知数量不是 Android Auto 弹窗保证。
- 不支持任意联系人主动拨号、通讯录浏览或完整 Telecom 通话界面。回拨仅在微信原通知有真实回拨动作时可用。
- 不获取麦克风、不代理通话音频，汽车扬声器和麦克风能否用于通话由微信、手机与车机决定。转交成功不等于已接通。
- 用户在荣耀 100 Pro / Android 16 / 微信 8.0.72 上测试 1.1.6：消息可查看但不弹窗、回复不成功；通话显示未提供接听按钮。本次 1.1.8 尚未上车验证，不能宣称上述问题已解决。
- 本地语音指令复用了 Android Auto 消息动作入口，是实验性兼容方式，不等同于 Google 官方认证的微信通话集成。

## 安装和停车验收

1. 将 `Fahrmony_1.1.8-personal.apk` 传到手机，核对交付的 SHA-256 后安装。包名 `com.fahrmony.app.personal`，使用单独的个人签名，与原版共存。测试时关闭原版的通知桥接以避免重复提醒。
2. 打开个人版，授予通知发送与通知使用权；如系统对侧载应用限制敏感设置，先核实安装包来源，再在应用信息中按系统提示处理。
3. 在 Android Auto 开发者设置中启用“未知来源”，重新连接。车机具体菜单随 Android Auto 版本变化。
4. 保持微信允许显示通知正文。应用的“通讯”页可开启隐藏发送者与正文；该选项也会阻止车机朗读正文。
5. 停车并连接 Android Auto 后，请他人连续发送两条不同消息：分别记录是否弹窗、数字图标是否增加、能否打开。若出现回复入口，再核实联系人实际收到回复，不能仅看助手说“已发送”。
6. 再请他人发起微信语音来电：提示无可用操作时不能代接；可能没有接口，也可能未识别。若有“接听”，从本地指令入口执行，并核实双向音频和挂断。随后打开手机端“通讯 → 通知诊断”，记录“原始按钮、穿戴按钮、同应用按钮、快捷输入、可用操作、提醒级别、旧式车载会话、车载回复凭据、车载输入”。这些记录只保存在内存，不含联系人、正文或回复内容。
7. 取消来电后旧操作应失效；通话操作最多保留 90 秒，普通消息提醒最多保留 10 分钟。多条通知互不覆盖，撤销通知使用权后停止桥接。

不建议在实际驾驶中进行首次测试。这里没有修改微信程序、使用私有协议、添加无障碍自动点击或要求 Root。

## Spotify 与 Android Auto

不替换 Android Auto，也不修改 Spotify。此版移除了车机断开时抢占全局音频焦点的调用，避免该路径打断 Spotify。仍保留上游的媒体桥接、媒体卡片及自动播放，尚不是“仅微信通知”版；建议关闭 Fahrmony 自动播放。不保证所有车型不存在卡片或音频竞争。通知朗读和真实电话也可能正常压低或暂停音乐，恢复行为需实测。

## 构建

依赖：Node.js 24+、pnpm 11.19.0、JDK 21、Android SDK 36。设置 `ANDROID_HOME` 后执行：

```sh
pnpm install --frozen-lockfile
pnpm audit --audit-level=moderate
pnpm run build
pnpm exec cap sync android
cd android
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Windows 使用 `gradlew.bat`。Capacitor sync 必须在 Gradle 启动前完成，不要并行运行二者，sync 会重新生成 Cordova 模块。

Release 产物需要签名。交付 APK 使用单独生成的个人密钥签名，不是原作者签名。密钥备份独立交付，切勿提交进源码仓库。后续升级需保留同一密钥。

`.github/workflows/android.yml` 已提供安装依赖、审计、构建、单元测试和 Lint 工作流。动作按提交 SHA 固定。它生成 CI debug APK，不使用个人密钥；CI debug 包不能直接覆盖个人 release 包。GitHub 连接器写入返回 403 Resource not accessible by integration，浏览器未登录；工作流尚未推送或在 GitHub 实际执行，本次 APK 在本地工作目录编译。

## 安全修复范围

1. 移除聊天发送者、正文、原始通知 extras 和语音输入的日志输出，阻止消息内容进入 Logcat、诊断列表与 IPC 日志。
2. 修复关闭预览设置此前未生效的问题，默认使用私密通知可见性；切换设置清除已有转发通知。
3. 关闭 Android 备份，限制 FileProvider 只共享专用缓存子目录，禁止明文网络。
4. 删除 Google Fonts 网络请求，增加 WebView 页面的 CSP；更新提示固定为本 fork 的 HTTPS Releases 页面。
5. 媒体浏览连接校验包名与 UID 并使用系统媒体控制信任检查；IPC 校验发送 UID；限制导出 Activity 的链式启动目标；关闭外部媒体按键广播入口，导出服务的 start Intent 不执行播控，保留 MediaSession 通道。老式广播唤醒播控行为可能与上游不同。
6. 普通消息移除本地指令伪回复，仅转交真实 RemoteInput；回复最多 2000 字，不写入日志。通话本地指令仅对应真实能力。
7. 使用每条通知独立标识及短期随机操作令牌，防止通知 ID 碰撞、旧令牌复用；撤回、更新、超时和权限断开使相关能力失效，内存记录最多 64 条。
8. 已识别为视频的来电不提供接听或回拨，避免从车机触发摄像头。尊重原操作的设备解锁要求；拒绝未知、重复或其他 UID 创建的通话操作，不发送自动接听命令。
9. 修复 Android 15+ dataSync 前台服务超时后可能崩溃的问题；系统超时停止服务，不能保证无限后台保活。
10. 修复构建工具链中 `xcode > uuid` 的已知中危依赖问题，补齐构建配置、Gradle wrapper 和归档校验值。

这是源码定向审查与修复，不是渗透测试或“无漏洞”证明。通知监听权限本身可读取敏感通知，系统权限应仅授予可信构建。

## 接口依据

- https://developer.android.com/training/cars/communication/notification-messaging
- https://developer.android.com/training/cars/communication/calling
- https://developer.android.com/reference/android/app/Notification.CallStyle
