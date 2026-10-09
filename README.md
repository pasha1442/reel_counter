# Reel Counter

Android app that counts every Instagram Reel and YouTube Short you watch, shows the cost as a
living brain, and (Pro) blocks the Reels/Shorts screen at your daily limit. Product plan:
[Research and MVP plan.md](Research%20and%20MVP%20plan.md).

## Status (MVP, first build)

| Area | State |
|---|---|
| Detection engine (rules, sessions, de-dup, debounce, scroll-distance fallback, kill switch) | Done, 45 JVM unit tests |
| YouTube Shorts counting | **Verified** on YouTube 20.10.41 / Android 16 emulator |
| Instagram Reels counting | Rules are unverified starters. Confirm IDs on a real phone first |
| Onboarding (disclosure, permission, notifications, battery, limit, live 3-reel test) | Done |
| Brain (5 stages, wince, blink, poke, heal carry-over, sparkles) | Done (Compose Canvas; Rive-ready API) |
| Home, History (7/30 days, by hour, day list), Limits, Settings | Done |
| Nudges at 50/80/100%, live status-bar count, home-screen widget | Done (widget not yet checked on a launcher) |
| Counter health check + one-tap fix (every 15 min + live) | Done |
| Block overlay (Pro) with Leave and Firm unlock (tap the promise's words, 3/day) | Done, verified over YouTube |
| Paywall | UI done; billing is a local stand-in (debug "Pretend to be Pro") |
| Firebase (remote rules pack, Crashlytics, Analytics), RevenueCat | Not started. Needs project keys |
| V1/V2 (Battles, strictness levels UI, schedules, session caps, more apps, iOS) | Not started |

## Build and run

Requirements: JDK 17+ (21 used), Android SDK with platform 37, build-tools 36+.

```bash
export JAVA_HOME=~/.local/jdks/jdk-21.0.12.1+1   # or your JDK
./gradlew test                  # all unit tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease  # minified, about 4 MB (unsigned)
```

Install the debug APK and turn the service on in **Settings > Accessibility > Reel Counter**.
Sideloaded builds on Android 13+ show "Restricted setting": open App info, then the ⋮ menu, then
*Allow restricted settings*.

On an emulator you can enable the service from a shell:

```bash
adb shell settings put secure enabled_accessibility_services \
  com.reelcounter.android.debug/com.reelcounter.service.ReelAccessibilityService
```

Before `adb install -r`, turn the service off (`settings delete secure enabled_accessibility_services`),
or Android can mark it "crashed" and stop sending events until it is toggled.

Debug builds have: *Settings > Debug* (pretend Pro, record detection sessions, replay onboarding),
*Home > Debug: simulate a reel*, and detection logs (`adb logcat -s ReelDetect`, no screen content).

## Modules

```
:app               navigation shell, widget (Glance), midnight job, DI for build config
:service           AccessibilityService, controller, block overlay, notifications, health worker
:feature:*         onboarding, home, history, limits, settings (+ paywall)
:core:design       theme (tokens, Fredoka/Nunito), brain, shared components
:core:data         Room (events + daily totals), typed DataStore, repositories, contracts
:core:detection    pure Kotlin: detection engine, rules pack, verifier, replay harness
:core:limits       pure Kotlin: limit engine, unlock rules, brain math, nudges, streaks
:core:model        pure Kotlin: shared types
build-logic/       convention plugins (AGP 9, Kotlin 2.4, Compose, Hilt, KSP)
```

The engines are plain JVM modules, so every counting rule is tested without a device.

## How counting works

`ReelAccessibilityService` only listens to Instagram, YouTube and the home screen (added at runtime so
we know when the user leaves; its content is never read). Each event is copied on the main thread and
processed in order on one worker thread by `CounterController` → `DetectionEngine`:

1. **Surface check:** a rule's view ID (e.g. `reel_recycler`) is visible and covers most of the screen.
2. **Advance:** the pager settled on a new item. With an item index (`fromIndex == toIndex`) that index is
   the reel. Without one (YouTube), each settled swipe's scroll distance is snapped to whole pages of the
   pager's height. Scroll events under-report distance (~94% of a page on YouTube), so snapping per swipe
   prevents drift.
3. **De-dup:** indexes / page positions seen in this sitting don't count again; an in-memory fingerprint
   (hash of author + caption) is used where the rules name those views. Nothing is stored.
4. **Debounce:** a reel must stay 500 ms after the previous count (fast flicks count only where you land).
   A timer tick lets a settled swipe count even when the app sends no further events.
5. **Sittings:** leaving for under 10 s resumes the same sitting (no recount of the reel on screen).

Findings from testing real YouTube that shaped the service config:
- YouTube marks its Shorts pager "not important for accessibility" → `flagIncludeNotImportantViews`.
- It sends the pager's scroll event followed by two parent views' events; any `notificationTimeout`
  batching keeps only the last one → `notificationTimeout="0"` (the engine throttles tree reads itself).
- Its channel/title overlay has no view IDs, so YouTube uses no fingerprint.
- YouTube shows its own "Turn on accessibility controls for the video player?" prompt when any
  accessibility service is on. Worth a line in onboarding or the FAQ.

### Rules pack

`core/detection/src/main/resources/rules/default_rules_pack.json`: per app and version range: surface,
pager and fingerprint view IDs, plus a `broken` kill switch ("Counting paused" instead of wrong numbers).
Unmatched versions fall back to a generic full-screen pager heuristic (marked *estimated*).
`RulesPackVerifier` (ECDSA P-256) is ready for signed remote packs.

### Recording a replay test

1. Debug build, *Settings > Debug > Record detection sessions* on.
2. Scroll while counting reels by hand.
3. `adb pull /sdcard/Android/data/com.reelcounter.android.debug/files/recordings/`
4. Set `handCount` in the file's first line, copy it to `core/detection/src/test/resources/recordings/`.

`ReplayTest` requires every recording to be within 3% of its hand count (the plan's 97% target).

## Design system

Built from the `ui-ux-pro-max` recommendations for a playful, youth-focused wellbeing app: vibrant,
block-based, dark first (deep navy `#0F0F23`, violet primary, rose `#E11D48` for high-energy actions),
Fredoka headings + Nunito body (bundled variable fonts, OFL), one icon family (Material Symbols
Rounded), 56 dp pill buttons with press-scale feedback, ≥ 44 dp touch targets, reduced-motion support
(system animator scale 0 disables loops). Chart colours were validated for colour-blind separation and
contrast in both themes (`#8B5CF6`/`#F43F5E` dark, `#6D28D9`/`#E11D48` light).

## Deliberate differences from the plan

- **Typed JSON DataStore** instead of Proto DataStore: same typed, crash-safe storage, no protoc step.
- **Canvas brain** instead of Rive for now: same one-number API (`rot` 0–100), swap in a `.riv` later.
- **Custom Canvas charts** instead of Vico: seven to thirty columns didn't justify the dependency.
- **`:service` module + pure JVM engines**: the plan's `:core:detection` held the service; splitting it
  keeps the engine testable and gives the block overlay (UI) a home.
- **Firm unlock = tap the promise's words in order** instead of typing: an accessibility overlay can't
  reliably take keyboard focus over another app. The `phraseMatches` rule is kept for later.
- **Brain carry-over:** rot includes damage left from previous days (35% after a bad day, 10% after a good
  one), so "heals overnight, faster after a good day" is real rather than a daily reset.
- **No INTERNET permission and `allowBackup=false`** in the MVP: nothing leaves the phone.
- **compileSdk 37** (current AndroidX requires it); targetSdk stays 36 as planned.

## Next steps

1. Verify Instagram view IDs on a real phone (`adb shell uiautomator dump` while in Reels; note that
   uiautomator temporarily suspends other accessibility services) and record replay sessions.
2. Firebase project: Remote Config + Cloud Storage for signed rules packs, Crashlytics, Analytics
   (counts only), the daily breakage alarm.
3. RevenueCat + Play Billing behind `EntitlementRepository`; Play Console products and trial.
4. Play policy pack: Accessibility declaration, disclosure video, Data safety form, privacy policy.
5. Test matrix across Xiaomi, Samsung, Oppo, Vivo, Pixel (health check + brand steps).
