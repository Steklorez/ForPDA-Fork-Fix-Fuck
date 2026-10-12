# ForPDAFFF

**ForPDA Fork Fix F\*\*\*** — the good old [4pda.to](https://4pda.to) client, dug out of its grave, given some makeup, and put back to work.

You know that joke about the flight attendant? *"Dig her up, put some lipstick on her, and..."* — yeah. That's this repo.

## Why

[ForPDA New](https://github.com/RadiationX/ForPDA) was the cleanest, most minimal 4pda client ever made. Then the author moved on (respect, radiationx), the site kept changing its markup, and the app slowly rotted: empty screens, dead comments, missing buttons, an editor that wipes your posts.

Somebody else wrote a shiny new client from scratch. It's big, it's loud, it has a thousand toggles. We don't care. We want *this* one back — old, but not useless.

## Rules of the necromancy

1. **Do not touch the UI.** The look is the whole point. If it looked like that in 2021, it looks like that now.
2. **No new features.** We fix what's broken. That's it. No "wouldn't it be cool if".
3. Fix stuff the forum actually complains about first.

## What's fixed so far

See [CHANGELOG.md](CHANGELOG.md). Short version:

- News comments load again
- "Quote" is back in the text selection menu (Android 12+)
- Editing your own post no longer opens an empty editor (and no longer nukes the post on save), and the editor closes after saving
- Article karma stops throwing exceptions on every open
- Attachments are read fully before upload
- Parser patterns are bundled and pinned, the dead remote no longer overrides them
- Notifications work in the background again (they used to arrive only while the app was open)
- Instant push notifications through FCM, with the minute check kept as a safety net; Settings → Notifications → Notification type lets you choose push or background check only
- Notifications for favorites and mentions lead with the topic title, the author goes below
- Fixed the push receiver ANR, and push now registers right after login
- Fixed the network error caused by a stale cookie that no longer parsed (`okhttp3.Cookie.name()` on null)
- Yandex Metrica removed: nothing is reported to the original author's account anymore

## Build

- JDK **17** (kapt from Kotlin 1.7 dies on JDK 21 — don't even try)
- Android SDK with platform 29 and build-tools
- `sdk.dir` in `local.properties` (or `ANDROID_HOME`)

```
gradlew assembleDevDebug       # ru.forpdateam.forpda.fff.debug, signed with the public debug key
gradlew assembleStableRelease  # ru.forpdateam.forpda.fff -> ForPDAFFF-1.0.4-fff.apk
```

Package is `ru.forpdateam.forpda.fff`, so it lives next to the original ForPDA, not on top of it.

### Your own signing key

Debug builds use the debug key from `keystore.properties` (it is public, same as the original's).
For a release build with your own key, create `signing/release.keystore.properties`:

```
RELEASE_STORE_FILE=path/to/your.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

The same four variables are also read from the environment. Without a key the release APK comes out unsigned.
Never commit your key; `signing/release.*` is git-ignored.

### Official releases

Releases are built by GitHub Actions (`.github/workflows/release.yml`) straight from the sources in this repository,
on a `v*` tag or by hand. The release key is stored in the repository's Actions secrets and is not part of the sources.
Pull requests only run a plain debug build without secrets (`build-check.yml`).

The release certificate SHA-256, to check your download against:

```
FA:31:29:AA:A3:43:38:FE:86:C2:39:25:92:E1:4D:03:A8:0C:1C:85:1D:9C:66:D3:D9:B4:A2:12:ED:CC:88:15
```

## Open source and privacy

All the source code is open (GPLv3), nothing is hidden. No analytics, no trackers, and no data goes to third parties. The app talks only to 4pda.to, GitHub (update check) and, if instant push is on, Google's push service. Pick "Background check" in Settings → Notifications to skip push entirely.

## Credits

Source of the original: [RadiationX/ForPDA](https://github.com/RadiationX/ForPDA) (GPLv3).
All the original work — [RadiationX](https://github.com/RadiationX) and the ForPDA team (iSanechek, Морфий, slartus and everyone in the [4pda topic](https://4pda.to/forum/index.php?showtopic=820313)).
This fork just does the dirty work.

## License

GPLv3, same as the original. See [LICENSE](LICENSE).

<div align="center">

![](https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12&height=90&section=header)

### ☕ Нравится ForPDAFFF? Можно угостить автора кофе!

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Donate-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/I3I0273OYI)
[![Boosty](https://img.shields.io/badge/Boosty-Donate-F15F2C?style=for-the-badge&logo=boosty&logoColor=white)](https://boosty.to/steklorez)

![](https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12&height=60&section=footer)

</div>
