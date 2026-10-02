# Dojo Log

An offline Android app for logging martial arts training. It has a dark theme only.

The app requests **no permissions at all** (not even internet access), so everything you
log stays in a local database on your phone.

## Features

- **Training calendar**: a month view where every martial art has its own colour, and the
  brighter the colour, the better that day's session was rated. An art keeps its colour for
  good, even when old sessions are deleted or renamed. Unrated sessions are hatched, and a
  day with several arts is split into stripes. The grid shows whole weeks, so the first and
  last rows include days of the previous and next month (drawn as smaller tiles); tap one
  to jump to that month. Use the arrows to change month, or tap the month's name to pick any
  month (each shows its training days). The rating key below the grid is drawn in the
  colour of the selected day's art, with that day's rating marked; on a day without
  training it takes the colour of the art you have logged most. Tap a day to see or add
  its sessions. Monthly totals and your weekly training streak sit under the calendar.
- **Year overview**: a GitHub-style grid of the whole year under the calendar, one square
  per day in the art's colour and brighter for better ratings. Filter it by art, tap a day
  to see what you trained, and jump to it in the calendar.
- **Session log**: date, duration (quick picks from 45 minutes to 2 hours), martial art (it
  remembers the ones you use), session type (class, open mat, sparring, private, solo,
  competition) and notes.
- **Opponents**: sparring and competition sessions list who you faced. Each matchup gets a
  result (win, loss, draw or none), a 1–10 rating of how it went and a comment, and the same
  person can be added for several rounds.
- **Techniques per session**: pick techniques from your library or create them as you go.
  For each one you can log reps, a 1–5 star execution-quality rating and a note.
- **Session rating**: five categories (Technique, Conditioning, Sparring, Focus, Effort),
  each scored 1–10. Leave a category at 0 if it didn't apply. The overall score is the
  average of the rated categories, or you can set it by hand.
- **Technique stats**: number of sessions, total reps, average quality, first and last
  practised, practice frequency per month, a quality trend chart and the full history.
  The library can be searched, filtered by category and sorted.
- **Overall stats**: totals for 30 days, 90 days, 12 months or all time, an activity chart,
  the trend of your overall rating, average score per category, your sparring and
  competition record, most practised techniques and time per martial art. The activity,
  rating, breakdown and technique charts can each be limited to one martial art.
- **Record by opponent** (Stats → *Record by opponent*): everyone you have faced, with
  wins–losses–draws, win rate, average rating and when you last met, filterable by art. Each
  person has a page with their club, grade, weight and your notes on them, a rating trend
  and every matchup.
- **Export & import** (the ⋮ menu on Calendar or Stats): saves everything to one JSON file
  you keep wherever you like, and reads it back, for example after reinstalling the app or on
  a new phone. An import adds what the app doesn't have yet and skips sessions it already
  has, so importing the same file twice is safe; it can also replace all data instead.

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
> start logging for real. Until then, use *Export & import* to save your data to a file
> before uninstalling, and import it into the new install.

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
├── data/          Room entities, DAOs, database (with migrations) and TrainingRepository
│   └── backup/    Export file format and import planning: pure Kotlin, unit tested
└── ui/
    ├── calendar/  Calendar screen (start screen)
    ├── session/   Session detail, session editor, technique and opponent pickers
    ├── techniques/ Technique library and per-technique stats
    ├── opponents/ Record by opponent and per-person pages
    ├── backup/    Export & import screen
    ├── stats/     Overall stats
    ├── components/ Cards, stat tiles, star rating, bar and line charts
    ├── navigation/ Bottom navigation and routes
    └── theme/     Dark colour scheme and the validated chart palette
```
