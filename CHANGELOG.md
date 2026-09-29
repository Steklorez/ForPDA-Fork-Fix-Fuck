# Changelog

All notable resurrections of ForPDAFFF. Based on ForPDA New 1.0.1 (`develop` branch of the original).

## [Unreleased] — "She's breathing"

### Fixed
- **News comments** — the comments tab was a blank white screen. 4pda moved comment text from `<p>` to `<div>`, the homemade HTML parser tripped over a null and silently died. Now it reads both.
- **Article karma** — `NumberFormatException: "[0"` on every article open. Karma JSON got nested; regex now only eats numeric arrays.
- **Quote in text selection menu** — on Android 12+ you selected text and got Chromium's stock *Translate / Copy / Share* instead of *Copy / Quote / Select all*. The WebView was overwriting our menu after we built it. We now rebuild it after every refresh.
- **Editing a post** — the editor opened empty, so saving replaced your whole post with whatever you typed. The form regex was too picky about attribute order on the edit page.
- **Editor stuck after saving** — post saved fine, but the editor screen never closed (same for the full reply form syncing back). A library bump in 2025 replaced `exitWithResult()` with `sendResult()` and forgot the *exit* part.
- **Attachments** — files were read with `InputStream.available()`, which lies for `content://` URIs (cloud/photo pickers). Now the whole stream is read before upload.
- **Parser patterns** — bundled pack synced with the author's last remote version and pinned as v100, so the abandoned remote can't roll back our fixes. Also the loaded version was never remembered, so patterns got re-downloaded on every check.

### Known dead bodies (not fixed yet)
- Forum list and forum search are empty for guests: `act=search` is behind an anti-bot wall now.
- Forum list for logged-in users shows up only after a manual "Refresh forums".
- Real-time notifications: `ws://app.4pda.to/ws/` refuses connections. No live QMS/mention notifications.
- Random logouts, scroll jank — reported on the forum, not investigated yet.
