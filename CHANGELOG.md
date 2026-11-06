# Changelog

All notable resurrections of ForPDAFFF. Based on ForPDA New 1.0.1 (`develop` branch of the original).

## [1.0.11-fff]

### Fixed
- **Error messages in plain words** — instead of raw exception text ("The source did not signal an event", "Response{code=451…}", "Unable to resolve host") the app says what happened and what to do: no answer in time, 4pda.to unreachable (check the internet or use a VPN), secure connection error, sign-in required (401), access denied (403), page not found (404, with the page path), access restricted for this address (451), forum server error (5xx), no connection. A screen closed mid-load no longer pops "Job was cancelled".
- **Countdown in "wait" dialogs** — the too-many-requests dialog and the forum's "wait N seconds/minutes" box show a shrinking line and "осталось 1:23" (Retry-After, the forum's own number, or about a minute for a 429 without one); at zero it says "Можно повторить". A repeated error while it is open only extends the countdown, no second dialog.

## [1.0.10-fff]

### Fixed
- **Clear message when the forum refuses to serve** — a Cloudflare check ("4PDA .:. Всё в порядке, но..."), too many requests (429) or a server that is down (502/503/504/52x) used to show a "Google Captcha" toast or a raw "Response {code=…}" for two seconds. Now a dialog says what happened and what to do (wait, pass the check in a browser, sign in for search), with the "Retry-After" wait when the server sends one, the code and the address. It stays until "OK" is tapped; one at a time, however many requests fail. Search without signing in gets the Cloudflare check on every request, that one is covered too.
- **Forum error messages stayed for two seconds** — the forum's own error box (wrong password or captcha at sign-in, "wait N seconds/minutes" limits and the like) was a toast, gone before it could be read. It is a dialog with the forum's text now, closed by "OK".

## [1.0.9-fff]

### Added
- **Post font** — Settings → Appearance → Font: System (default, nothing changes), Roboto, Classic (Arial-like, Liberation Sans) or Classic wide (Verdana-like, DejaVu Sans). The bundled fonts carry their own bold and italic, so bold text shows up even when the phone uses a third-party system font without a bold face. Applies to topics, search, QMS, news, forum rules and announcements; takes effect the next time a page opens.

## [1.0.8-fff]

### Fixed
- **SOCKS5 proxy with a login did not work** — Android asks for SOCKS credentials as a "server" request, the app only answered "proxy" ones, so the proxy got the system user name ("root") and no password. The saved login and password are sent now.
- **Network errors name the page** — "Response {code=404, …}" now ends with the address that failed, so a bug report says exactly which request broke.

## [1.0.7-fff]

### Added
- **Proxy** — Settings → Network: HTTP or SOCKS5, host, port, optional login and password. Forum requests and images go through it; switching takes effect without a restart. Off by default. Pictures that a topic page loads by itself inside the web view and the instant-events websocket still go direct.

- **Update available dot** — the app checks for updates at most once a day and, when a newer version exists, shows a small red dot on the menu button, on Settings and on "Check for updates".

### Fixed
- **"Check for updates" screen was blank in the dark theme** — white text on a hard-coded white background. It follows the theme now.
- **New messages did not show up in an open QMS chat** — the push and the minute check never told an open chat about new messages, the manual catch-up sent the topic id where the user id belongs, and a failed websocket lookup just gave up. An open chat now loads new messages right after the push.

## [1.0.6-fff]

### Added
- **Mark all favorites as read** — a tick in the toolbar of Favorites. A short "N of M will be marked as read" confirmation, then every unread topic gets a small spinner on its right; the app walks through them in the background, marks each one read on the forum and, right when it happens, the spinner goes away and the title fades to the read look.
- **"Sign in required" plate** — the Forum and Favorites screens show a centered plate instead of an empty screen and a "Google Captcha" toast when you are not signed in. Tap it to open the sign-in screen; the list loads in the background right after signing in.

### Fixed
- **Unread badge of a QMS chat stayed** — opening a chat straight from a notification (or the 4PDA system messages) did not clear its unread counter on the tab and in Contacts. Opening a chat now resets it.

## [1.0.5-fff]

### Changed
- **Forum topic link** — the "Other" menu has the "Discussion" item again, next to GitHub, and opens this fork's own 4pda topic.
- **APK name** — the release file is now `ForPDAFFF-1.0.5.apk`, without the `-fff` suffix.
### Fixed
- **Shifted dates and times** — the forum prints every date in the time zone of your forum profile, and the app shows it as is, so a phone in another zone saw times off by hours. After login (and again only when the phone's zone or the account changes) the app copies the device's UTC offset into the profile's "Time zone" setting of the forum, leaves all other profile settings as they were, and remembers it did so. Only the zone, daylight saving and auto-zone switch are touched.

## [1.0.4-fff]

### Changed
- **App name** — the launcher label is now "ForPDA FFF" (beta: "ForPDA FFF BETA"), so it no longer looks like the original ForPDA next to it.

## [1.0.3-fff]

### Changed
- **Nothing goes to the original author anymore** — the update and parser-pattern check now reads this repository (`update/check.json`) instead of the original author's Bitbucket. The "Other" menu no longer links to his Telegram, profile, Play Market and Bitbucket pages; only this repository's GitHub is left.

## [1.0.2-fff] — "She's breathing"

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
