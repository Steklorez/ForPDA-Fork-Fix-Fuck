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
- **Notifications** — only arrived while the app was on screen: Android 8+ blocks background `startService()`, and the periodic check died silently. Now it binds instead. Plus a once-a-minute check of the site's tiny events counter (`appbk.4pda.to/er/...`, ~30 bytes) triggers the real QMS/favorites check only when something actually happened. Free, no keys, no "activation".
- **Instant notifications** — the once-a-minute check was fine for a corpse, but the living get pushes. The fork now asks Google Play Services for an FCM token under the Firebase project number 4pda's own app uses (Google hands tokens for a project number to anyone who asks), then tells 4pda's server "user N, this token, these kinds of notifications" through a WebSocket over TLS (`appbk.4pda.to`, certificate name checked, no plain TCP anywhere in this path). Login there is your own `pass_hash` cookie, it only ever travels inside the tunnel, and your password is nowhere near it. When something happens, 4pda pokes the phone and the usual events check runs right away: about 0.1 s from push to notification in our test. The push itself is never read, it only wakes the check. No Google Play Services, TLS refuses to come up, or 4pda changes its mind: nothing breaks, the minute check keeps working.
- **Push or background check, your pick** — Settings → Notifications → Notification type. "Push" (default) registers for instant pushes and keeps the minute check as a safety net. "Background check" skips push entirely: nothing is registered, any push that still shows up is ignored. For people who prefer boring and reliable over fast.
- **Network error "Attempt to invoke virtual method ... okhttp3.Cookie.name() on a null object reference"** — a saved cookie that no longer parsed (stale `cf_clearance` and friends) went into the cookie jar as `null`, and every request after that blew up on it. Cookies that fail to parse are now simply dropped, like they should have been.
- **Notification order** — favorites and mentions now lead with the topic title and put the name below ("New post from Nick"). You follow topics, not the several million people writing in them.
- **Push receiver ANR** — the push handler held the process for 20 s while Android gives a push broadcast 10, so the first real push ended with an "app isn't responding" dialog. Holds for 8 s now.
- **Push after login** — push was registered only when the app started, so a fresh install that logged in later got no instant notifications until the next restart. Registers right after login now.
- **Yandex Metrica removed** — it was still reporting crashes and sessions to the original author's account.
- **Parser patterns** — bundled pack synced with the author's last remote version and pinned as v100, so the abandoned remote can't roll back our fixes. Also the loaded version was never remembered, so patterns got re-downloaded on every check.

### Known dead bodies (not fixed yet)
- Forum list and forum search are empty for guests: `act=search` is behind an anti-bot wall now.
- Forum list for logged-in users shows up only after a manual "Refresh forums".
- Random logouts, scroll jank — reported on the forum, not investigated yet.
