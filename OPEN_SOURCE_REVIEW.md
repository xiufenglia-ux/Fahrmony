# 微信接入 Android Auto：开源方案评估

核对日期：2026-09-14。目标：荣耀 100 Pro、Android 16、微信 8.0.72，通过手机投射 Android Auto；保留 Spotify 使用体验。

## 结论

目前最值得实施的是“原生消息通知桥接 + 完整读取微信真实回复能力”，并把媒体控制从消息功能中分离。消息弹窗与语音回复优先验证；接听、挂断和主动拨号作为独立能力评估。

没有找到针对上述手机、系统和微信版本，经过实机验证、无需改系统且完整支持弹窗、回复、接听和拨号的开源成品。这是本次检索范围内的结论，不代表技术上永远不可能。

本次发现了现有实现的实际遗漏，因此不应继续只调通知优先级，也不应把“没有普通按钮”解释为微信一定没有回复接口。

## 最重要的源码发现

Nevolution 的微信插件用 `Notification.CarExtender(n).unreadConversation` 获取旧式车载会话，再读取其中的 `replyPendingIntent` 与 `remoteInput` 生成真正的快捷回复。这与普通 `notification.actions`、Wearable 扩展是不同的读取路径。我们的 1.1.7 没有检查它。

依据：[WeChatDecorator.kt](https://github.com/Nevolution/decorator-wechat/blob/14c807aa87eba2afef3697cd95ae7b89476e1bf2/src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatDecorator.kt)、[MessagingBuilder.kt](https://github.com/Nevolution/decorator-wechat/blob/14c807aa87eba2afef3697cd95ae7b89476e1bf2/src/main/java/com/oasisfeng/nevo/decorators/wechat/MessagingBuilder.kt)、[Android UnreadConversation API](https://developer.android.com/reference/android/app/Notification.CarExtender.UnreadConversation)。

1.1.8 已补查这一公开接口：只有原应用创建的可写凭据及有效输入键才允许转交；普通动作存在歧义时拒绝猜选。不复制 Nevolution 的聊天日志、替身 APK 或兼容模式实现。旧式接口存在于开源代码，并不证明微信 8.0.72 在你的设备上仍会提供它。

## 方案比较

| 方案 | 实际实现与证据 | 对本任务的判断 |
| --- | --- | --- |
| Nevolution 微信插件 | 读取微信旧式车载会话，转成 MessagingStyle，转交原回复凭据。检索到的最新提交为 2023-05-09；还有较新系统的兼容性问题报告。 | 最值得借鉴的回复适配路径；应提取思路并在当前代码中验证，不直接宣称老 APK 兼容 Android 16。 |
| AA Notification Forwarder | 使用高优先级通知、MessagingStyle 和车载不可见动作转发提醒。README 明确声明回复不能工作；源码使用 dummy PendingIntent。 | 可作为“车机能否接收桥接通知”的对照，不能解决真实回复或通话。不能照搬占位回复设计。 |
| wechat_video_call | 使用无障碍打开微信、搜索联系人、点击通话菜单。读到的代码包含硬编码节点 ID、等待、选择首个搜索结果及视频选项兜底。 | 证明界面自动化可以尝试拨号；不等于锁屏可接听、正确联系人可保证或车机音频已打通。不能原样用于你的语音通话功能。 |
| Assists | 无障碍开发框架，README 展示微信自动接听示例，并提示较新微信版本的节点兼容问题。 | 可作为以后编写受限、由用户触发的界面操作适配器的参考；本次没有获得目标设备上稳定接听的验证。 |
| Fermata 镜像路线 | 项目文档列出较新 Android 上的侧载限制与 Root／特定适配器等条件。 | 不符合优先保留原生 Android Auto、Spotify、低维护成本的目标，不作为首选。 |

直接依据：[Nevolution 项目](https://github.com/Nevolution/decorator-wechat)、[其兼容问题 #61](https://github.com/Nevolution/decorator-wechat/issues/61)、[AA Notification Forwarder README](https://github.com/ztNFny/AANotificationForwarder/blob/9011f2e969f68c142c877b7a84f5b0d8b8439f28/README.md)、[通知实现](https://github.com/ztNFny/AANotificationForwarder/blob/9011f2e969f68c142c877b7a84f5b0d8b8439f28/app/src/main/java/xda/xlafbk/aanotificationforwarder/NotificationHelper.java)、[微信拨号源码](https://github.com/davidche1116/wechat_video_call/blob/fb69036e208c977bdf780cac9a9d6cc55a06e8aa/android/src/main/kotlin/com/dc16/wechat_video_call/WeChatAccessibility.kt)、[Assists](https://github.com/peacejoyi/Assists)、[Fermata Android 14+ 说明](https://github.com/AndreyPavlenko/Fermata/discussions/432)。

## 弹窗：能改什么，不能证明什么

Google 标准消息流程要求 MessagingStyle、回复动作及已读动作；打开通知时先触发已读，之后才可能回复。因此补全真实回复接口，并保留已读后的操作凭据，是有明确依据的修正。[官方消息流程](https://developer.android.com/training/cars/communication/notification-messaging)

但这些条件齐全不等于一定弹窗。AA Notification Forwarder 的问题区也有无法投射、仅数字角标及延迟展示的报告；项目作者没有确认这些现象都由同一个代码原因造成。不能把某个论坛用户成功截图当作荣耀设备的保证。[角标问题及维护者回复](https://github.com/ztNFny/AANotificationForwarder/issues/18)

需要在同一次车机连接中分别记录：微信原通知的能力、桥接通知的渠道级别、车机弹窗表现、回复接收方是否实际收到。若完整能力存在仍不弹窗，下一步应对比最小原生通知示例与 Fahrmony 媒体桥接；不应再叠加假回复入口掩盖问题。

## 通话与 Spotify

原生 Android Auto 通话控制要求应用接入 Telecom，并实现接听、断开等回调。仅把微信来电文字改成 CallStyle，或者登记一个不掌握真实微信通话状态的 Telecom 通话，并不会自动获得微信接听和双向音频能力。[官方通话要求](https://developer.android.com/training/cars/communication/calling)

如果原通知没有可用通话操作，无障碍是可研究的备选，但必须针对微信 8.0.72 实机节点、锁屏、通话结束及蓝牙音频做验证。只允许明确匹配的语音操作；找不到、存在多个目标、锁屏阻止或超时就失败退出，不选择第一个联系人、不回退到视频、不自动接听。这一适配尚未加入 APK。

为减少 Spotify 竞争，长期应提供默认的“仅微信通知”模式，禁用 Fahrmony 的媒体浏览服务、媒体会话、自动播放和焦点请求。1.1.8 仍保留现有媒体模块；上一版仅移除了断连时的全局焦点抢占，因此不能把当前包称为完全不影响 Spotify 的通知专用版。

## 本次落实与验收门槛

- 1.1.8：补查旧式 CarExtender 回复能力，增加“旧式车载会话／车载回复凭据／车载输入”诊断字段。
- 延续上一版的隐私、凭据验证及失效控制；没有新增 Root、无障碍权限或在线聊天代理。
- 添加三项 Android 设备端测试，检查普通按钮为空时仍能取到车载回复、缺少接口不制造回复、不可写凭据被拒绝；编译成功与设备实际执行必须分别记录。
- 只有车机真的弹窗、联系人真的收到回复，才将对应功能标为已验证。当前没有目标设备测试结果，不发布“完整微信通话已实现”的结论。

源码许可核对：Nevolution 为 Apache-2.0，AA Notification Forwarder 自有代码为 CC BY-NC 4.0，wechat_video_call 为 MIT。此轮采用公开接口思路独立实现，没有把这些项目整体合并进 Fahrmony。
