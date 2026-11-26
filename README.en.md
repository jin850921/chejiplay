<img src="docs/icon.png" width="72" height="72" alt="車機Play">

# 車機Play（com.cheji.play）

[繁體中文（台灣）](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

**This guide as a web page, with screenshots and install steps:** <https://jin850921.github.io/chejiplay>

CarPlay and Android Auto, one tap on the head unit — A card-based download and launch hub: it fetches the newest DiPlay／DiAuto APKs, and once installed a card opens them straight away.

[Download APK](https://github.com/jin850921/chejiplay/releases/download/v1.0/chejiplay-1.0.apk) ／ [Source on GitHub](https://github.com/jin850921/chejiplay) ／ [Releases](https://github.com/jin850921/chejiplay/releases)

No root, no ADB, no phone-side app.

The APK goes on the car, not on your phone. Pure Java and framework APIs: no Gradle, no AndroidX, no third-party dependencies. Whether the head unit allows installing and auto-start is decided by its own system.

**DiPlay and DiAuto focus on BYD head units; other brands may work but are not guaranteed.**

## Install it on the car

Install on the head unit, not on an iPhone or an Android phone. The app works offline; only the update check and the APK download need a network.

1. While parked, download `chejiplay-1.0.apk` and open it with the head unit's own installer; installing APKs from other sources must be allowed.
2. Open 車機Play. The two rows of cards read the install state from the head unit directly.
3. Add 車機Play to your head unit's boot-start list so this screen opens every time the display wakes. Where that list lives depends on the brand.
4. Tap a card to enter CarPlay or Android Auto. If that project is not installed yet, the card downloads it first; long-press while downloading to cancel.

No ADB and no root are required. This app never modifies or repackages DiPlay／DiAuto - the download location is always the official asset in their own Releases.

## What the app does

- Two rows of cards: DiPlay (CarPlay) and DiAuto (Android Auto) on top, Google Maps and one or two apps you choose on the bottom row.
- Checks GitHub Releases once a day and never opens a reminder screen - a new version only lights a red dot on the ⋮ menu.
- The card is the action: not installed means download, installed means open. Long-press reveals cancel／change／remove.
- A finished APK is handed to the head unit's own installer; nothing goes through an intermediate service.
- The ⋮ menu has nine rows: check versions, three download sources, batch download, this app's update, shortcuts, the guide page, language and theme.

## Shortcuts and icons

- Every icon in the bottom row comes from the installed app itself; when the head unit reports one, it is drawn full-bleed with no colour override.
- Only when nothing is found does the built-in default appear: an official-colour pin for Google Maps, a plus sign for an empty slot.
- After an install, a removal, or an icon change in that app, the card repaints on resume or on the package broadcast - no restart needed.
- The Google Maps shortcut only opens the app. It never downloads and never checks for updates; Google Play on the head unit maintains Maps.
- Up to two manual slots: a tap picks or opens an app, a long-press changes it or removes the card.

## Launch animation

A cold start covers the screen with a 1.5-second animation. Switching theme or language repaints the UI without replaying it.

- The car outline drives in from the left, the logo badge pops into place, then the title and hint fade in.
- The bar shows launch progress: it follows the animation from 0 to 90 percent, and the last 10 percent only unlocks when the first layout pass really returns.
- The layer fades out as soon as layout completes; a 3-second backstop always closes it, and on that path the bar keeps the value it actually reached.

## Interface language

Traditional Chinese, Simplified Chinese, English, Japanese and Korean, plus Auto, which follows the head unit's locale.

- The screen repaints immediately; no reinstall and no lost settings.
- App names, brand names and the names of the apps on your head unit are deliberately not translated - translating them would make those apps unfindable.
- Translations are written by the maintainer and have not been reviewed by native speakers. To change a sentence, edit one line in `L.java`.

## Updates and signing

Versions are always written as `version_name(version_code)`, for example 1.0(1).

- The daily check reads this repository's Releases: tag `v1.0`, asset `chejiplay-1.0.apk`.
- Checking only compares version numbers; it never installs. Downloads and updates always start from the ⋮ menu rows.
- A new version must be signed with the same key or the head unit refuses the upgrade install. The key and its password file are not in this repository.
- The batch row downloads DiPlay and DiAuto only; updating 車機Play is its own menu row.

## Not yet verified on a head unit

No real car and no emulator were used during development. All verification is static: badging, resource IDs, the DEX string pool and bytecode are read back out of the signed APK, plus a translation-coverage check and 1280×720 reference renders.

- Real appearance, whether the head unit grants the install permission, and how long each translated line wraps - all of that needs a vehicle.
- Whether your head unit reports Google Maps' own icon (when it does not, the card shows the built-in pin rather than a blank tile).
- How many characters a toast fits in the head unit's font. Lengths are estimated at 14sp and every sentence is kept inside a width already known to fit.

When reporting a problem, include the head unit brand, DiLink／Android version, APK version and the time it happened.

[Report an issue](https://github.com/jin850921/chejiplay/issues/new/choose)

## Screens

- [Home screen (light): DiPlay and DiAuto on top, Google Maps plus two custom shortcuts below.](docs/screenshots/01_%E4%B8%BB%E7%95%AB%E9%9D%A2_%E6%B7%BA%E8%89%B2.png)
- [Home screen (dark): the same layout, colours follow the theme.](docs/screenshots/02_%E4%B8%BB%E7%95%AB%E9%9D%A2_%E6%B7%B1%E8%89%B2.png)
- [Shortcuts off to on: the bottom row goes from absent to three equal-width cards.](docs/screenshots/07_%E4%B8%BB%E7%95%AB%E9%9D%A2%E5%BF%AB%E6%8D%B7%E9%88%95%E5%89%8D%E5%BE%8C%E5%B0%8D%E6%AF%94.png)
- [Manual slots: before picking apps, and after both slots are filled.](docs/screenshots/09_%E6%89%8B%E5%8B%95%E6%AC%84%E4%BD%8D_%E6%9C%AA%E6%94%BE%E8%88%87%E5%B7%B2%E6%94%BE%E5%B0%8D%E6%AF%94.png)
- [Four moments of the launch animation (cold start only, 1.5 s total).](docs/screenshots/06_%E8%BC%89%E5%85%A5%E5%8B%95%E7%95%AB.png)

Every image is a 1:1 1280×720 reference render built from the actual colours and layout in `res/`, not a design mock-up; nothing has been verified on a real head unit yet.

---

Build and release details - `node build.cjs <versionName> <versionCode>`, how the signing key and its password file are handled, and the tag and asset naming conventions for Releases - are documented in the Traditional Chinese [README.md](README.md). The app has no Gradle and no AndroidX, and can be rebuilt offline from `res/` and `src/`.

車機Play is an independent community project with no affiliation to Apple, Google or BYD. DiPlay and DiAuto are separate community projects on GitHub; this app only fetches their newest APK and opens it, always from the official asset in their Releases. CarPlay and its icon belong to Apple Inc.; Android Auto and its icon belong to Google LLC.
