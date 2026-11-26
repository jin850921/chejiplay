<img src="docs/icon.png" width="72" height="72" alt="車機Play">

# 車機Play（com.cheji.play）

[繁體中文（台灣）](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

**网页版（同一份内容，含截图与安装步骤）：** <https://jin850921.github.io/chejiplay>

车机屏幕上的 CarPlay／Android Auto 入口 — 一个卡片式的下载与启动入口：抓 DiPlay／DiAuto 的最新版 APK，装好之后点一下就进入。

[下载 APK](https://github.com/jin850921/chejiplay/releases/download/v1.0/chejiplay-1.0.apk) ／ [GitHub 源代码](https://github.com/jin850921/chejiplay) ／ [Releases](https://github.com/jin850921/chejiplay/releases)

不需要 root、不需要 ADB、不需要手机端 App。

APK 装在车机上，不是装在手机。纯 Java／framework 专案，没有 Gradle、没有 AndroidX、没有任何第三方相依。能不能安装、能不能加入开机自启动，都由车机系统决定。

**DiPlay 与 DiAuto 以 BYD 车机为主力，其他品牌不保证可用。**

## 装在车机上

装在车机，不是装在 iPhone 或 Android 手机。整个 App 离线也能用，只有检查更新与下载 APK 需要网络。

1. 停车时下载 `chejiplay-1.0.apk`，用车机自己的安装程序开启；车机要允许安装其他来源的 APK。
2. 打开「车机Play」，主画面两排卡片会直接读车机上的安装状态。
3. 把「车机Play」加入车机的开机自启动清单，之后每次开车机屏幕就自动打开这里。清单的名称与位置依车机品牌不同。
4. 点卡片就进入 CarPlay 或 Android Auto；那款还没装的话会先下载，下载中长按卡片可以取消。

本 App 不需要 ADB、不需要 root，也不修改、不重新打包 DiPlay／DiAuto 的 APK——下载位置永远是对方 Releases 的官方资产。

## 这支 App 做什么

- 主画面两排卡片：上排 DiPlay（CarPlay）与 DiAuto（Android Auto），下排是 Google Maps 快捷钮与你自己挑的 1–2 个 App。
- 每天检查一次 GitHub Releases 的最新版，不跳任何提醒画面；有新版只在右上角 ⋮ 亮起红点。
- 卡片本身就是动作：没装就下载，已装就开启；长按才出现取消下载／换 App／移除。
- 下载好的 APK 交给车机自己的安装程序，不经过任何中间服务。
- 右上角 ⋮ 一共 9 列：查版号、三个下载来源、批量下载、本 App 更新、快捷钮设置、说明页、语言、主题。

## 快捷钮与图示

- 下排那一列的图一律取车机上那套 App 自己的图示，查得到就满版显示、不压色。
- 查不到才退回内建预设图：Google Maps 是官方配色的图钉，还没放 App 的栏位是「＋」。
- 装好、移除，或那套 App 换了图示，回到本 App 或收到套件广播就重画，不用重开程序。
- Google Maps 快捷钮只负责开启，永不下载、永不检查更新——Maps 的更新交给车机上的 Google Play。
- 手动栏位最多两栏：点一下是选 App 或开启 App，长按换一个或从主画面移除。

## 载入动画

冷开机进主画面时先盖一层载入动画，总长 1.5 秒；换主题、换语言只重绘画面，不会重播。

- 车身线稿从画面左侧驶入，logo 徽章弹到位，标题与提示接着淡入。
- 进度条显示启动进度：跟着动画从 0 走到 90%，最后 10% 要等首轮排版真的回来才解锁。
- 排版一完成就整层淡出；保险上限 3 秒，走这条时进度留在实际到达的数值，不会假装读完。

## 界面语言

界面有繁体中文、简体中文、English、日本語、한국어，外加「自动」照车机语系。

- 选完立刻重绘画面，不用重装、不会掉设置。
- App 名称、品牌名与车机上那套 App 的名字刻意不翻——翻了就找不到那支 App。
- 译文由专案维护者撰写，没有母语人士校过；要改任何一句，改 `L.java` 那一列就好。

## 更新与签名

版号一律写成 `version_name(version_code)`，例如 1.0(1)。

- 每日检查读的是这个 repo 的 Releases：tag `v1.0`、资产文件名 `chejiplay-1.0.apk`。
- 检查只查版号，不会自己安装；下载与更新一律从 ⋮ 菜单里那几列手动执行。
- 换装新版必须用同一支签名密钥，否则车机会拒绝升级安装；密钥与密码档不放在 repo 里。
- 「DiPlay＋DiAuto 两个都下载」是批次列，不含本 App；更新车机Play 是菜单里单独那列。

## 尚未装上车机验证

开发过程没有接过实车车机，也没有模拟器。所有验证都是静态的：从签好的 APK 挖出 badging、资源 ID、DEX 字符串池与位元码比对，加上翻译覆盖率检查与 1280×720 对照图。

- 实际外观、车机有没有给安装权、各语言换行之后的文字长度，都要装上车机才知道。
- 车机到底有没有回报 Google Maps 自己的图示（查不到时那一格是内建图钉，不会变空白）。
- 消息（Toast）在车机字体下放得下几个字——句子长度按 14sp 估算，每句都刻意留在已知放得下的宽度以内。

遇到问题请附车机品牌、DiLink／Android 版本、APK 版号与发生时间，开一个 issue。

[回报问题](https://github.com/jin850921/chejiplay/issues/new/choose)

## 画面

- [主画面（浅色）：上排 DiPlay／DiAuto，下排 Google Maps 与两栏自定义快捷钮。](docs/screenshots/01_%E4%B8%BB%E7%95%AB%E9%9D%A2_%E6%B7%BA%E8%89%B2.png)
- [主画面（深色）：同一个版面，颜色跟着主题换。](docs/screenshots/02_%E4%B8%BB%E7%95%AB%E9%9D%A2_%E6%B7%B1%E8%89%B2.png)
- [快捷钮关闭 → 开启：下排从整列不存在变成三张等宽卡。](docs/screenshots/07_%E4%B8%BB%E7%95%AB%E9%9D%A2%E5%BF%AB%E6%8D%B7%E9%88%95%E5%89%8D%E5%BE%8C%E5%B0%8D%E6%AF%94.png)
- [手动栏位：还没选 App → 两栏都选好之后。](docs/screenshots/09_%E6%89%8B%E5%8B%95%E6%AC%84%E4%BD%8D_%E6%9C%AA%E6%94%BE%E8%88%87%E5%B7%B2%E6%94%BE%E5%B0%8D%E6%AF%94.png)
- [载入动画的四个时间点（冷开机跑一次，总长 1.5 秒）。](docs/screenshots/06_%E8%BC%89%E5%85%A5%E5%8B%95%E7%95%AB.png)

每张都是 1280×720 的 1:1 对照图，依 repo 里 `res/` 的实际色值与版面渲出来，不是设计稿；尚未装上车机验证。

---

建置与发布细节——`node build.cjs <versionName> <versionCode>`、签名密钥与密码档的处理、Releases 的 tag 与资产命名约定——以繁中的 [README.md](README.md) 为准；这支 App 没有 Gradle、没有 AndroidX，离线也能从 `res/` 与 `src/` 重建。

车机Play 是独立社群专案，与 Apple、Google、BYD 没有任何隶属关系。DiPlay、DiAuto 是 GitHub 上的独立社群专案，本 App 只负责抓最新版 APK 与开启，下载位置一律是对方 Releases 的官方资产。CarPlay 及其图示属 Apple Inc.，Android Auto 及其图示属 Google LLC。
