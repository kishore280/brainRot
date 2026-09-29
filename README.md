# Brainrot

<img src="app/src/main/res/drawable-nodpi/ic_launcher_brain.png" width="96" align="right" alt="Brainrot icon">

An Android app that counts the Instagram Reels you swipe through, and shows you a brain that rots
a little more with every one.

- **Counts reels, on the phone.** An accessibility service watches which list scrolled and where
  it landed. It never reads captions, usernames or messages.
- **A silent notification while you scroll.** "Brain rotting · 12 reels", with today's brain,
  then "✓ 42 reels in 18 min" when you stop. No sound, no pop-up.
- **Now scrolling, on your site** (optional, see below).
- **Home-screen widget.** Today's count and the brain's current state, on your home screen.
- **Today screen.** The count, time spent, sittings, reels per hour, and seconds per reel.

---

## The rot

The brain has seven stages, driven by today's count. Counting resets at local midnight.

| Reels today | 0 | 10 | 25 | 50 | 100 | 175 | 250+ |
|---|---|---|---|---|---|---|---|
| Stage | Fresh | Bruised | Foggy | Fried | Mushy | Rotting | Brain rot |
| | <img src="service/src/main/res/drawable-nodpi/brain_stage_0.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_1.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_2.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_3.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_4.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_5.png" width="64"> | <img src="service/src/main/res/drawable-nodpi/brain_stage_6.png" width="64"> |

The thresholds live in one place, [`BrainRot.kt`](service/src/main/java/com/reeltracker/service/BrainRot.kt),
so the notification, the widget and the app always agree.

### The notification

Built the way [Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler) builds its
now-playing notification (`PanoNotifications.android.kt`): a low-importance channel ("Brain
rotting": no sound, no vibration, no pop-up), one notification updated in place, the brain's
silhouette as the small icon and today's brain as the picture.

| When | It shows |
|---|---|
| In Reels | "Brain rotting · 12 reels" and "Foggy · 30 today"; it stays while you scroll |
| After | "✓ 42 reels in 18 min"; swipe it away, or it goes by itself after an hour |

Android 13+ asks once for permission to show notifications. Counting works without it.

### The home-screen widget

Shows the brain for today's stage, the count, and the stage name. Tapping it opens the app.

- Add it from the app (**Add to home screen** under the count), or long-press the home screen →
  Widgets → Brainrot.
- It updates on every counted reel. When nothing is being counted, Android's minimum refresh
  (30 minutes) is what rolls it back to 0 after midnight, so the reset can lag by up to half an hour.

### Now scrolling, on your site (this fork)

Set **Your site** on the Today screen (an address and a token), and the phone tells your site
while you scroll: "scrolling" when Reels opens, the running count every 30 s, and "stopped" with
the total when you leave Reels, leave Instagram or turn the screen off. Only the count and times
are sent: `POST <address>` with `Authorization: Bearer <token>` and
`{"app":"instagram","scrolling":true,"reels":12,"today":40,"minutes":6,"perReel":9,"started":<ms>,"ended":null}`
(`reels`: this sitting; `today`, `minutes`, `perReel`: today so far, as on the Today screen).
Leave the token empty and nothing leaves the phone. The logic is `ScrollSession` (core-model,
tested) and `SiteReporter` (service).

### Releases

This fork's app id is `com.kishore.brainrot`, so it installs next to the original Brainrot
(`com.reeltracker`, signed with another key) instead of failing to install over it.

Every push runs the tests, builds a test APK, and installs the release build on an Android 15
emulator with `tools/install-check.sh` (adb install, open the app, turn on the service, check that
its `:bg` process runs). The release build is shrunk with R8 (about 2.7 MB instead of 26 MB), as
Pano Scrobbler builds its release. The service and the widget run in their own process (`:bg`), as
Pano's scrobbler does, and the site settings are a multi-process DataStore. A release comes from a tag
(`git tag v1.2 && git push origin v1.2`) or from **Actions → build → Run workflow** with a version
such as `v1.2` (the workflow makes the tag). It builds an APK signed with your key and publishes it
as a GitHub release, so it installs over the app
on your phone as an update. The key is the secret `DEBUG_KEYSTORE_BASE64` (the base64 of the
`debug.keystore` that signed the installed app) in the repo's **release** environment
(Settings → Environments → release).

---

## Install and set up

1. **Build and install** (see [Building](#building)), or install a built APK with
   `adb install -r app-debug.apk`.
2. **Turn on the service:** Settings → Accessibility → Installed apps → **Brainrot** → On. The app's
   status pill (top right) opens this screen for you, and turns green ("Tracking") once it's running.
3. **Let it run in the background:** Settings → Apps → Brainrot → Battery → **Unrestricted**.
   Without this, many phones (OPPO/OnePlus/Xiaomi in particular) stop the service after a while, and
   the app shows "Not running".
4. Open Instagram, go to Reels, and swipe. The "Brain rotting" notification appears.

---

## How counting works

```
Instagram ──AccessibilityEvent──▶ ReelAccessibilityService ──UiSignal──▶ Detector ──Decision──▶ ReelRepository ──▶ Room
                                     (service, Android)                  (core-detect, pure)         (core-data)
                                            │                                                          │
                                            └──── EnterReels / ExitReels ──▶ notification    Flow<Int> ─┴─▶ widget, Today screen
```

1. **Only three event types are subscribed to:** a view scrolled, a window changed, a tab was
   selected ([`reel_service.xml`](service/src/main/res/xml/reel_service.xml)). The service reads no
   view text and never walks the view tree. It reads a scrolling view's id and the pager position
   Android already puts on the event.
2. **`SignalMapper`** turns each event into a small `UiSignal`. Everything from other apps is
   dropped before the event's source is even read.
3. **`Detector`** is a pure state machine: *outside Reels* ↔ *in Reels*. It enters when the Reels
   tab is selected or the Reels pager scrolls. It exits when any other tab, window or app takes over.
   **Accuracy policy:** when unsure, exit. A missed reel makes the number a little low; a phantom
   reel would make it wrong forever.
4. **`IndexStrategy`** confirms a reel the first time the pager settles on a page index it hasn't
   seen:
   - an abandoned half-swipe never changes the index, so it doesn't count;
   - swiping back lands on an index already seen, so it doesn't count again;
   - a refreshed feed restarts indices, and is detected (item count shrank *and* index went
     backwards) and started as a new epoch rather than re-counting.
5. **`ReelRepository`** stores one row per reel, with the unique key `(sessionId, key)`, so a
   duplicate is dropped by the database rather than by logic. No counter is ever stored: today's
   count, hourly chart and sittings are all computed from event times when they're read. That means
   midnight isn't an event, and nothing can drift.

### Privacy

- No internet permission (check the merged manifest), no analytics, no accounts. Everything stays in a local Room database
  (`reels.db`).
- Never reads captions, usernames, comments or messages.
- The debug export (below) contains event types, view ids, positions and hashes, never text.

---

## Building

**Requirements**
- JDK 17
- Android SDK with platform 35 (`compileSdk 35`, `minSdk 29`, i.e. Android 10+)
- Gradle 8.9 (the wrapper downloads it)

`local.properties` points at your SDK:

```properties
sdk.dir=D\:/android-sdk
```

**Commands** (use `./gradlew` on macOS/Linux)

```bash
gradlew.bat :app:assembleDebug
```

```bash
gradlew.bat :app:installDebug
```

```bash
gradlew.bat :core-detect:test :core-model:test
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

---

## Project structure

| Module | What's in it | Android? |
|---|---|---|
| `core-model` | `UiSignal`, `Decision`, `DayStats` (sittings, hourly buckets) | No, pure Kotlin |
| `core-detect` | `Detector`, `IndexStrategy`, `ReelsContext`, `SignalMapper`, `SignalRecorder`, plus replay tests | No, pure Kotlin |
| `core-data` | Room database, `ReelDao`, `ReelRepository` | Yes |
| `service` | `ReelAccessibilityService`, the notification (`BrainNotifier`), `SiteReporter`, the widget (`ReelWidgetProvider`), `BrainRot` stages, sprites | Yes |
| `app` | Compose UI: `MainActivity`, `TodayScreen`, `HourlyChart`, theme, launcher icon | Yes |
| `spike` | M0 forensic logger, a separate app (`com.reeltracker.spike`) | Yes |

`core-detect` must never depend on Android. That boundary is what lets the detector run against
recorded captures in plain JVM tests.

Key files:

- [`ReelsContext.kt`](core-detect/src/main/kotlin/com/reeltracker/detect/ReelsContext.kt): Instagram's view ids. **This is what breaks when Instagram updates.**
- [`BrainRot.kt`](service/src/main/java/com/reeltracker/service/BrainRot.kt): stage thresholds, names and sprites.
- [`BrainNotifier.kt`](service/src/main/java/com/reeltracker/service/BrainNotifier.kt): the silent notification.
- [`SiteReporter.kt`](service/src/main/java/com/reeltracker/service/SiteReporter.kt): "now scrolling" to your site.
- [`ReelWidgetProvider.kt`](service/src/main/java/com/reeltracker/service/ReelWidgetProvider.kt): the home-screen widget.

---

## When Instagram changes and counting stops

Counting depends on Instagram's internal view ids (`clips_viewer_view_pager`, `clips_tab`, and
`*_tab`), last verified on Instagram 447.0.0.55.81, OnePlus CPH2467, Android 15. An Instagram
update can rename them. The usual symptom: "Last one counted" stops moving while you're clearly
scrolling Reels.

1. Reproduce it: open Reels, swipe through a few, leave Reels.
2. In Brainrot, tap **Export debug capture**. It writes `Download/reel-capture-<time>.json` with the
   last 2,000 signals and what the detector decided for each.
3. Look at the `sourceId` values on `Scrolled` and `Selected` entries to find the new pager and tab
   ids, and update [`ReelsContext.kt`](core-detect/src/main/kotlin/com/reeltracker/detect/ReelsContext.kt).
4. **Add the capture to the regression corpus.** Copy it to
   `core-detect/src/test/resources/corpus/`, named `<date>__ig<version>__<device>__expect<N>.json`
   where N is the number of reels you actually watched, e.g.
   `2026-10-02__ig450.0.0.12.34__oneplus-cph2467__expect12.json`. `DetectorReplayTest` replays every
   file there and asserts its count, so a fix for one Instagram version can't silently break an
   older one.
5. Run the tests (`gradlew.bat :core-detect:test`).

If the export isn't enough, e.g. Instagram stopped reporting page indices entirely, use the
**spike** app. It logs every Instagram accessibility event (with any text reduced to a hash and
length) to a JSONL file in its app storage. Summarise a capture with:

```powershell
pwsh tools/m0-summary.ps1 captures/capture-<timestamp>.jsonl
```

It reports, for each scrolling view, whether Instagram populates `toIndex`/`itemCount`, the
information `IndexStrategy` depends on. A new counting method would be a second `DetectionStrategy`.
The detector already picks the first strategy whose `supports()` accepts the signals it sees.

---

## Customising

- **Stage thresholds and names:** `THRESHOLDS` and `LABELS` in `BrainRot.kt`.
- **Sprites:** `service/src/main/res/drawable-nodpi/`. `brain_stage_0` … `brain_stage_6` are the
  stages, transparent PNGs cut from the original sprite sheet; `ic_noti_brain` is the notification
  icon (the launcher's monochrome brain, white on transparent). Replace any file with one of the
  same name.
- **Launcher icon:** `app/src/main/res/drawable-nodpi/ic_launcher_*.png`, an adaptive icon
  (background, foreground, and a monochrome layer for themed icons).

---

## Known limitations

- **Instagram only.** YouTube Shorts and others would each need their own `ReelsContext` and probably
  their own strategy.
- **Verified on one device and Instagram version** (see above). Other builds may use other view ids.
- **The service can be killed** by aggressive battery managers. Set battery use to Unrestricted, and
  watch the status pill: "Not running" means Android stopped it. Toggling it off and on in
  Accessibility settings restarts it.
- **Widget midnight reset** can lag up to 30 minutes when nothing is being counted (Android's
  minimum widget refresh).
- **Package name** is still `com.reeltracker` from the project's original name. Changing it would
  install Brainrot as a separate app and lose existing history.
