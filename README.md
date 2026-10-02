# Dojo Log

An offline Android app for logging martial arts training. It has a dark theme only.

The app requests **no permissions at all** (not even internet access), so everything you
log stays in a local database on your phone.

## Features

- **Training calendar**: a month view where each day you trained is coloured by that day's
  best session rating. Swipe or use the arrows to change month, and tap a day to see or add
  its sessions. Monthly totals and your weekly training streak sit under the calendar.
- **Session log**: date, duration, martial art (it remembers the ones you use), session type
  (class, open mat, sparring, private, solo, competition) and notes.
- **Techniques per session**: pick techniques from your library or create them as you go.
  For each one you can log reps, a 1–5 star execution-quality rating and a note.
- **Session rating**: five categories (Technique, Conditioning, Sparring, Focus, Effort),
  each scored 1–10. Leave a category at 0 if it didn't apply. The overall score is the
  average of the rated categories, or you can set it by hand.
- **Technique stats**: number of sessions, total reps, average quality, first and last
  practised, practice frequency per month, a quality trend chart and the full history.
  The library can be searched, filtered by category and sorted.
- **Overall stats**: totals for 30 days, 90 days, 12 months or all time, an activity chart,
  the trend of your overall rating, average score per category, most practised techniques
  and time per martial art.

## Getting the app

### Download a build

Every push runs the **Android build** workflow (GitHub → *Actions*). Open the latest run,
download the `dojo-log-apk` artifact and unzip it:

- `app-release.apk` is the one to install: it is optimised and much smoother.
- `app-debug.apk` is the debug build.

Copy the APK to your phone and open it. Android will ask you to allow installing apps
from that source.

> **Keeping your data across updates.** Android only installs an update over an existing
> app when both are signed with the same key. Without the setup below, each CI build is
> signed with a throwaway key, so installing a newer build means uninstalling the old one
> first, **which deletes your training log**. Set up a release key once (below) before you
> start logging for real.

### Stable signing key (recommended)

1. Create a keystore once and keep it somewhere safe, outside this repository:

   ```sh
   keytool -genkeypair -v -keystore dojolog-release.jks -alias dojolog \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. In the repository go to *Settings → Secrets and variables → Actions* and add:

   | Secret | Value |
   |---|---|
   | `DOJOLOG_KEYSTORE_BASE64` | output of `base64 -w0 dojolog-release.jks` |
   | `DOJOLOG_KEYSTORE_PASSWORD` | the keystore password |
   | `DOJOLOG_KEY_ALIAS` | `dojolog` |
   | `DOJOLOG_KEY_PASSWORD` | the key password (same as the keystore one unless you chose otherwise) |

From then on every release APK is signed with your key and installs over the previous one
with your data intact.

### Build it yourself

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration, or
from a terminal with the Android SDK installed:

```sh
./gradlew testDebugUnitTest   # unit tests for the stats logic
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Tech

Kotlin, Jetpack Compose (Material 3), Room (SQLite) and Navigation Compose. It needs
Android 8.0 (API 26) or newer.

```
app/src/main/java/com/dojolog/
├── domain/        Models and Stats: pure Kotlin with no Android code, unit tested
├── data/          Room entities, DAOs, database and TrainingRepository
└── ui/
    ├── calendar/  Calendar screen (start screen)
    ├── session/   Session detail, session editor and technique picker
    ├── techniques/ Technique library and per-technique stats
    ├── stats/     Overall stats
    ├── components/ Cards, stat tiles, star rating, bar and line charts
    ├── navigation/ Bottom navigation and routes
    └── theme/     Dark colour scheme and the validated chart palette
```
