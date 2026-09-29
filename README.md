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

## Build

- JDK **17** (kapt from Kotlin 1.7 dies on JDK 21 — don't even try)
- Android SDK with platform 29/34 build-tools
- `sdk.dir` in `local.properties`

```
gradlew assembleDevDebug       # ru.forpdateam.forpda.fff.debug
gradlew assembleStableRelease  # ru.forpdateam.forpda.fff -> ForPDAFFF-1.0.1-fff.apk
```

Package is `ru.forpdateam.forpda.fff`, so it lives next to the original ForPDA, not on top of it.

## Credits

All the original work — [RadiationX](https://github.com/RadiationX) and the ForPDA team (iSanechek, Морфий, slartus and everyone in the [4pda topic](https://4pda.to/forum/index.php?showtopic=820313)).
This fork just does the dirty work.

## License

GPLv3, same as the original. See [LICENSE](LICENSE).
