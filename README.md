# YTDL Manager for Android

The Android companion to the [YTDL Manager desktop app](https://github.com/Azeezhamzat/YTDL_Manager).
Paste a link, or share one from YouTube, TikTok or your browser, then pick
**Video** (with a max quality cap) or **Audio** (M4A or MP3). The file lands in
**Downloads/YTDL Manager** on your phone.

It's a native Kotlin + Jetpack Compose app built on
[youtubedl-android](https://github.com/yausername/youtubedl-android), which
packages Python, [yt-dlp](https://github.com/yt-dlp/yt-dlp),
[FFmpeg](https://ffmpeg.org/) and [QuickJS](https://bellard.org/quickjs/) for
Android. Everything runs on the phone. There are no accounts, logins, cookies,
analytics or ads.

## Legal notice

Only download videos you own, that are in the public domain, that carry a
licence permitting download, or for which you otherwise have the rights
holder's explicit permission. This application does not bypass DRM, CAPTCHAs,
paywalls, or other access controls. It uses no logins or cookies. It cannot
guarantee that every item on YouTube, TikTok, Twitter/X, Instagram, Facebook,
or Vimeo is legally or technically downloadable. You are responsible for
complying with applicable law, each platform's Terms of Service, and any
content licence. This notice is also shown inside the app on every screen.

## Contents

- [Install](#install)
- [Using the app](#using-the-app)
- [YouTube and the JavaScript runtime](#youtube-and-the-javascript-runtime)
- [Why it isn't on Google Play](#why-it-isnt-on-google-play)
- [Building from source](#building-from-source)
- [Releases and signing](#releases-and-signing)
- [Project layout](#project-layout)
- [Known limitations](#known-limitations)
- [Licence](#licence)

## Install

Requires **Android 10 or newer**.

### 1. Download the APK

On your phone, open the
[latest release](https://github.com/Azeezhamzat/YTDLManager-Android/releases/latest)
and download the file for your phone:

| File | For |
|---|---|
| `YTDLManager-<version>-arm64-v8a.apk` | Almost every phone made in the last ~8 years. **Pick this if unsure.** |
| `YTDLManager-<version>-armeabi-v7a.apk` | Older 32-bit phones |
| `YTDLManager-<version>-x86_64.apk` | Emulators, some Chromebooks and tablets |
| `YTDLManager-<version>-universal.apk` | Works on all of the above, but about 4× bigger |

Each APK is large (around 50–60 MB) because it carries its own Python and
FFmpeg.

### 2. Allow "Install unknown apps"

Android only installs apps from outside the Play Store if you allow the app
you open the APK with. You do this once:

1. Open the downloaded APK (tap the download notification, or find it in
   **Files › Downloads**).
2. Android says *"For your security, your phone currently isn't allowed to
   install unknown apps from this source."* Tap **Settings**.
3. Turn on **Allow from this source** for your browser or Files app, then go
   back.
4. Tap **Install**.

Play Protect may warn that the app is unrecognised, because it isn't from the
Play Store. Choose **Install anyway** (or **More details › Install anyway**).

The menu is under **Settings › Apps › Special app access › Install unknown
apps** if you want to switch it off again afterwards. Wording varies a little
between phone brands.

### 3. (Optional) Check the file

Each release has a `SHA256SUMS.txt`. On a computer:

```bash
sha256sum -c SHA256SUMS.txt --ignore-missing
```

Official APKs are signed with this certificate (SHA-256):

```
D9:C7:CB:6F:F9:60:29:0D:43:74:74:A2:BD:A8:EF:88:66:37:95:08:B1:8E:28:BA:1B:9E:B9:D7:F4:61:C7:C7
```

`apksigner verify --print-certs <file>.apk` (Android SDK build-tools) shows
the same value, lowercase and without colons.

### Updating and uninstalling

Install a newer APK over the old one: your history and settings are kept.
Every release is signed with the same key, so Android accepts the update. To
uninstall, long-press the app icon and choose **Uninstall**. Files you
downloaded stay in Downloads/YTDL Manager.

## Using the app

1. **Get a link in.** In the YouTube app (or TikTok, X, a browser…) tap
   **Share › YTDL Manager**. Or copy a link and tap **Paste** in the app.
2. The app fetches the **title and thumbnail**.
3. Choose **Video** and a **max quality** (Best, 1080p, 720p, 480p, 360p),
   or **Audio** as **M4A** or **MP3**.
   - Video is saved as MP4. With a cap, you get the best quality at or under
     it. If nothing fits under the cap, you get the best quality available
     rather than an error, the same rule as the desktop app.
   - M4A keeps YouTube's original AAC audio without re-encoding. MP3 is
     converted at the highest VBR quality.
   - Title and artist tags are written into files, and audio gets the
     thumbnail as cover art (Settings › Tags and cover art).
4. Tap **Download**. The **queue** shows progress, speed and time left. Tap ✕
   to cancel. Failed items explain why (tap for yt-dlp's own message) and can
   be retried.
5. Finished files move to **History**: open, share, or remove them (keeping or
   deleting the file).

Downloads keep going with the app in the background (a notification shows
progress), and resume after the phone restarts or the app is killed.

### Settings

- **Default type, max quality, audio format**: what the choices start at.
- **Wi-Fi only**: queued downloads wait for Wi-Fi or another unmetered
  network. Fetching a title and thumbnail still works on mobile data.
- **Downloads at once**: 1 (default) or 2. It's capped at 2 on purpose; see
  below.
- **yt-dlp**: shows the version, updates it automatically once a day
  (default on), and has an **Update now** button.

### When YouTube says "confirm you're not a bot"

YouTube sometimes blocks a network that has downloaded a lot recently with
*"Sign in to confirm you're not a bot"*. The block is tied to your
connection (IP address), not to one video. The app:

- explains this instead of showing yt-dlp's raw error;
- **stops the other queued YouTube items** instead of hammering YouTube with
  them (they show *Not started* and come back with **Retry failed**);
- never signs in, and doesn't offer to.

What helps: wait (often an hour or more), or switch between Wi-Fi and mobile
data, then retry. Keep **Downloads at once** at 1.

## YouTube and the JavaScript runtime

Since late 2025, yt-dlp needs a JavaScript runtime to solve YouTube's player
challenges. Without one, it sees only a few low-quality formats or none.
Here's how this app handles that, checked against youtubedl-android's source
and release artifacts:

- **youtubedl-android 0.18.1** (the latest release, November 2025) bundles a
  QuickJS binary (`libqjs.so`, about 1 MB per CPU type). It adds
  `--js-runtimes quickjs:<path>` to every yt-dlp command itself. Deno would
  have added about 95 MB and Node about 70 MB, which is why the library chose
  QuickJS.
- The challenge-solver scripts (`yt-dlp-ejs`) ship inside the official
  `yt-dlp` zipimport binary, which is the build this library runs and
  downloads when updating. Nothing else is needed.
- QuickJS is slower than Deno (a few seconds per video). The app passes
  yt-dlp a persistent `--cache-dir`, so solved challenges and player code are
  reused between downloads.
- The yt-dlp **bundled in the APK is 2025.11.12**, which YouTube has long
  since outdated. The app therefore **updates yt-dlp from its official GitHub
  releases on first launch and then at most daily**. Updates wait until no
  download is running. If YouTube fails, **Settings › Check for update now** is
  the first thing to try.

The emulator smoke test (`youTubeFormatsNeedTheJavaScriptRuntime`) checks this
end to end: it asserts YouTube offers 1080p or higher, which only happens when
the challenges are solved.

## Why it isn't on Google Play

Google Play's policies don't allow apps that download YouTube videos, so this
is distributed as an APK from GitHub Releases and installed by sideloading
(as [Seal](https://github.com/JunkFood02/Seal), YTDLnis and others are).
F-Droid would be an option in future.

## Building from source

You need **JDK 17** and the **Android SDK** (Android Studio includes both, or
use the command-line tools). `local.properties` with `sdk.dir=...` or
`ANDROID_HOME` must point at the SDK. Gradle itself comes from the wrapper.

```bash
# Unit tests (pure Kotlin, no device needed)
./gradlew testDebugUnitTest

# Debug APKs, one per CPU type, in app/build/outputs/apk/debug/
./gradlew assembleDebug

# Install on a connected phone or running emulator
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# On-device smoke tests: real yt-dlp + FFmpeg + MediaStore (needs internet)
./gradlew connectedDebugAndroidTest
```

The debug build installs next to a release build (its id ends in `.debug`).

| Tool | Version |
|---|---|
| Android Gradle Plugin | 9.5.1 (built-in Kotlin) |
| Gradle | 9.9.0 (wrapper, checksum-pinned) |
| Kotlin | 2.4.21 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 29 |
| youtubedl-android | 0.18.1 (`io.github.junkfood02.youtubedl-android`) |

## Releases and signing

`.github/workflows/release.yml` runs on tags `v*`. It builds and signs the
release APKs, prints their certificates, writes `SHA256SUMS.txt`, and
attaches everything to a GitHub Release. The tag must match `versionName` in
`app/build.gradle.kts`.

```bash
git tag v0.1.0
git push origin v0.1.0
```

### Why a long-lived key stored as a repository secret

Android installs an update only if it's signed with the **same key** as the
installed app. Otherwise the user has to uninstall first and loses their
history. So the key has to be:

- **the same for every release**: a key generated fresh in CI on each run
  would break updates every time;
- **available to CI without being in the repository**: the keystore is
  base64-encoded into an encrypted GitHub Actions secret. Secrets aren't
  exposed to pull requests from forks, and the workflow deletes the decoded
  file when it finishes;
- **backed up by you**: if it's lost, existing users can't update, ever. Keep
  the `.jks` file and its passwords in a password manager.

Create it once, outside the repository (`*.jks`, `*.p12` and `signing/` are
git-ignored anyway). With a JDK's `keytool`:

```bash
keytool -genkeypair -v -keystore ytdlmanager-release.p12 -storetype PKCS12 \
  -alias ytdlmanager -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=YTDL Manager"
```

Or without a JDK, using OpenSSL. This is how the project's key was made.
A PKCS#12 keystore uses one password for both the store and the key:

```bash
openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -nodes \
  -subj "/CN=YTDL Manager" -keyout key.pem -out cert.pem
openssl pkcs12 -export -name ytdlmanager -inkey key.pem -in cert.pem \
  -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 \
  -out ytdlmanager-release.p12
rm key.pem cert.pem
```

Then add four **repository secrets** (GitHub › Settings › Secrets and variables
› Actions), or with the GitHub CLI:

```bash
base64 -i ytdlmanager-release.p12 | gh secret set YTDLM_KEYSTORE_BASE64
gh secret set YTDLM_KEYSTORE_PASSWORD
gh secret set YTDLM_KEY_ALIAS --body ytdlmanager
gh secret set YTDLM_KEY_PASSWORD
```

To sign locally, export the same names (`YTDLM_KEYSTORE_FILE` holds the path
to the keystore) and run `./gradlew assembleRelease`. Without them,
`assembleRelease` produces unsigned APKs.

## Project layout

```
app/src/main/kotlin/io/github/azeezhamzat/ytdlmanager/
  core/        Pure Kotlin, unit-tested: yt-dlp options and format selector
               (ported from the desktop app), progress and error parsing,
               link extraction, file naming, queue rules, legal notice
  engine/      YtDlpEngine: the only code that calls youtubedl-android
  data/        Settings, download queue + history (JSON files), network state
  storage/     MediaStoreSaver: publishes files to Downloads/YTDL Manager
  work/        QueueWorker (WorkManager): runs the queue, notifications
  ui/          Compose screens: Download (link, preview, queue), History, Settings
app/src/test/          Unit tests for core/
app/src/androidTest/   On-device smoke tests (real downloads)
.github/workflows/     ci.yml (tests, lint, APKs, emulator), release.yml
```

How a download flows: the link is fetched with `yt-dlp -J` for the preview →
**Download** adds a task to the queue → `QueueWorker` runs yt-dlp into a
private work folder (resumable) → the result is copied into MediaStore
Downloads → it moves to History.

## Known limitations

- **One video at a time**: playlists and channels aren't supported yet. A
  playlist link says so instead of downloading hundreds of videos.
- **Android 10+ only.** Older versions lack the MediaStore Downloads API.
- **No logins**, so private, members-only, age-restricted and many
  Instagram/Vimeo items can't be downloaded. That's by design.
- **Large APK** (≈50–60 MB per CPU type) because Python and FFmpeg are
  bundled.
- **Each file is briefly stored twice**: yt-dlp writes to app storage, then
  it's copied to Downloads and the copy in app storage is deleted.
- **Cancelling during the merge step** stops yt-dlp, but an FFmpeg child
  process may finish a few seconds later (a youtubedl-android limitation).
  Its output is discarded.
- **Background restarts on Android 12+**: if Wi-Fi returns while the app is
  closed, Android may not allow the progress notification. The queue still
  runs, and anything Android stops is resumed later.
- **Upstream library issues to watch** (youtubedl-android has had no release
  since November 2025):
  - [#369](https://github.com/yausername/youtubedl-android/issues/369):
    FFmpeg's `librubberband.so` needs `libc++_shared.so`, which the FFmpeg
    package lacks. When FFmpeg runs through yt-dlp, which is how this app runs
    it, the library path includes the Python package, and that does contain
    `libc++_shared.so`. The MP3 smoke test covers this.
  - [#365](https://github.com/yausername/youtubedl-android/issues/365): a
    few WebP libraries inside the FFmpeg package aren't 16 KB-page aligned,
    which can break FFmpeg on the rare devices running 16 KB pages.
  - [#371](https://github.com/yausername/youtubedl-android/issues/371):
    QuickJS adds a few seconds per YouTube request.

## Licence

**GPL-3.0** (see [LICENSE](LICENSE)). The desktop app is MIT, but this app
links youtubedl-android, which is GPL-3.0, so the app as a whole must be GPL-3.0.

Bundled through youtubedl-android: yt-dlp (Unlicense), Python (PSF), FFmpeg
(GPL build), QuickJS (MIT), plus the libraries they depend on.
