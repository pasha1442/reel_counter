# Reel Counter App — Full Product & Build Plan

Sep 29, 2026 · @Akash Parashar

## Product vision

We build an Android-first app that counts every short video a person watches, shows the cost as a living brain, and stops the feed at a number they choose. We beat BrainPal by being more reliable, stricter when asked, and more honest about data.

**Who it is for:** students and young working adults who open Reels or Shorts dozens of times a day and lose hours without noticing. Set the Play Console target age to 13+ and state the app is not designed for children.

**Where BrainPal falls short, and our answer** (from its [Play Store listing and reviews](https://play.google.com/store/apps/details?id=com.brainrot.android)):

| BrainPal gap | Our answer |
| --- | --- |
| Counter stops; users must grant permission again and again | A health monitor that spots a dead counter within 15 minutes and fixes it in one tap |
| Unlocking became too easy after an update | User-chosen strictness, including a true Locked mode |
| Says data never leaves the device, but Data safety lists personal info | Counting stays on the phone; only daily totals sync, and only for Battles |
| Same brain stages for everyone (cracking at 100, gone at 200) | Brain decay scaled to each person's own limit |

**Product principles**

1. Count, don't estimate: one reel = one count, 97%+ accurate against a manual count.
2. Never fail silently: if counting breaks, the user knows within minutes.
3. Private by design: no captions, usernames or video content are ever stored.
4. Funny, not shaming: the brain teases; it never lectures.
5. The user sets the strictness, from a gentle nudge to a hard lock.

**North-star metric:** a user's average daily reels in week 4 compared with week 1. Target: 40% lower. Guardrail: day-30 retention above 20%.

## Feature scope

The MVP ships the counter, the brain and one strong blocking mode for Instagram Reels and YouTube Shorts only. Those two apps carry most short-video time, and doing two apps perfectly beats four apps badly.

### MVP (launch)

- **Guided onboarding:** explains the Accessibility permission, deep-links to the right settings screen, and adds brand-specific steps for Xiaomi, Samsung, Oppo and Vivo.
- **Live reel counter:** Instagram Reels and YouTube Shorts, counted per app and in total.
- **Brain visual:** five animated stages that react to each new reel and heal overnight.
- **Live count everywhere:** home-screen widget and an optional status-bar notification.
- **Daily limit with nudges:** gentle alerts at 50%, 80% and 100% of the limit.
- **History:** today, last 7 days, and a best-day streak.
- **Counter health check:** alerts the user if counting stops, with a one-tap fix.
- **Block at limit (Pro):** blocks only the Reels or Shorts screen, so DMs and search still work.
- **Pro subscription** with a free trial.

### V1 (about 2 months after launch)

- **More apps:** Facebook Reels, Snapchat Spotlight, and TikTok in countries where it is available.
- **Scroll Battles:** add friends by invite link, compare daily counts, lower count wins.
- **Strictness levels (Pro):** Gentle, Firm and Locked (details in the blocking section).
- **Schedules (Pro):** block reels during study hours or after bedtime.
- **Session cap:** a limit per sitting, such as 20 reels at a time.
- **Milestones and streaks:** badges for days under the limit.

### V2 (months 7 to 10)

- **Weekly insight card:** when and where the user scrolls most, shareable as an image.
- **Group battles:** leagues for a class, hostel or team.
- **Friend unlock:** a friend must approve an early unlock.
- **Replace the habit:** the block screen offers a one-minute alternative, such as a breathing exercise.
- **iOS app:** time-based version (see the iOS section).
- **More languages** for the top markets we see after launch.

**Deliberately left out:** ads (they would contradict the mission), and web or desktop tracking (BrainPal's users are on phones).

## Tech stack

We build fully native Android in Kotlin, with Firebase as the backend and RevenueCat for subscriptions. The core of this product is Android system access, so native code is the right tool.

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin, 100% native | The accessibility service, overlays and widgets are Android system features. Native code runs them with no bridge, and Kotlin is Google's recommended language. |
| UI | Jetpack Compose + Material 3 with a custom theme | Declarative UI with strong animation tools and far less code than XML views. |
| Architecture | MVVM with clean layers, multi-module Gradle | The detection engine sits in its own module with no UI code, so it can be tested and fixed on its own. |
| Dependency injection | Hilt | The Android standard; it injects into services, workers and ViewModels alike. |
| Async | Kotlin Coroutines + Flow | Accessibility events are a stream. Flow models that directly, and StateFlow drives the live counter. |
| Local database | Room (SQLite) | Stores every reel event on the phone. Reactive queries update the UI instantly. |
| Settings | Proto DataStore | Typed, crash-safe storage for limits, strictness and the service heartbeat. |
| Background jobs | WorkManager | Midnight rollover, sync and health checks that survive reboots. |
| Brain animation | Rive | A state machine driven by one number blends smoothly between brain stages. The same file runs on iOS later. Lottie only plays fixed timelines. |
| Charts | Vico | Charts built for Compose, for the history screen. |
| Home-screen widget | Jetpack Glance | Widgets written in Compose-style code. |
| Backend | Firebase: Auth, Firestore, Cloud Functions, Cloud Messaging, Remote Config, Storage | Offline-first Android SDKs. Remote Config lets us fix broken detection without an app update. Generous free tier. |
| Payments | Google Play Billing through RevenueCat | Handles receipts, trials, entitlements and paywall tests, and covers iOS later. [Free up to $2,500 monthly tracked revenue, then 1%](https://revenuecat.com/pricing). |
| Crashes and analytics | Firebase Crashlytics + Firebase Analytics | Free and in the same SDK family. We log counts and app events only, never content. |
| CI/CD | GitHub Actions + Gradle Play Publisher | Every merge builds, runs tests and uploads to the Play internal testing track. |
| Android versions | minSdk 28 (Android 9), targetSdk 36 (Android 16) | Android 9 added the scroll-distance data our detector uses. [Play requires API 36 for new apps and updates from Aug 31, 2026](https://developer.android.com/google/play/requirements/target-sdk). |

**Why not Flutter or React Native:** about 80% of the hard work is Android system code (accessibility, overlays, widgets, phone-brand quirks). A cross-platform layer adds a bridge and a second toolchain but saves little. The core feature cannot run on iOS anyway.

**Why Firebase over Supabase:** Supabase has better SQL, but Firebase gives us offline sync, Remote Config, push messages and crash reports in one place. That matters more for a small team.

**Later option:** Kotlin Multiplatform can share the brain-scoring and battle logic with the iOS app, once iOS starts.

## System architecture

All counting and blocking run on the phone, inside one accessibility service; the cloud only stores daily totals for Battles and delivers detection rules. That split keeps the app fast, private, and fixable without a Play Store release.

&#91;embedded content: System architecture · 9 parts on the phone, 4 in the cloud\]

Events flow into the detection engine, which saves one event per reel on the phone; the limit engine then decides whether to block. New detection rules arrive from the cloud, so a broken counter is fixed without an app update.

**Code modules**

- `:app` — navigation and app shell.
- `:core:detection` — the accessibility service and rules engine, with no UI code.
- `:core:data` — Room, DataStore and repositories.
- `:core:limits` — the limit engine and block controller.
- `:core:design` — theme, shared components and the Rive brain wrapper.
- `:feature:*` — onboarding, home, history, limits, battles and paywall screens.
- `:sync` — Firebase and WorkManager jobs.

## Reel detection engine

The detection engine turns raw accessibility events into exactly one "+1" per reel, using rules we can update from the server within hours. It is the heart of the product, so our most senior Android engineer owns it.

**Service setup**

- Limit `packageNames` to the supported apps, so the service never receives events from banking, chat or any other app.
- Listen to window changes, content changes and scroll events; turn on `canRetrieveWindowContent` and view-ID reporting.
- Set `isAccessibilityTool="false"`: we are honest that this is not a disability tool.
- Process every event on one background thread, never the main thread.

**What happens on each event**

1. **Filter:** drop events from any app not covered by the rules pack.
2. **Surface check:** confirm the full-screen Reels or Shorts player is on screen, by matching its view IDs and layout.
3. **Advance check:** confirm the vertical pager moved a full screen, using the scroll distance and a change in the visible item.
4. **Identity check:** build a short in-memory fingerprint of the new reel, such as a hash of the on-screen author and caption. Count it only if it differs from recent reels. The fingerprint is never saved.
5. **Debounce:** ignore a second advance within 500 ms (fast flicks, animations).
6. **Record:** write one event (app, time, session) to Room and update the live count.

Scrolling back to a reel already seen does not count again.

**The rules pack**

- A signed JSON file per supported app and app-version range, holding the view IDs and signals for the player, the pager and "reel changed".
- Remote Config holds the current pack version; the file sits in Cloud Storage. Phones check on app open and every few hours.
- A generic fallback (full-screen vertical pager with a video view) runs when no rule matches, and its counts are marked "estimated".
- A kill switch: if a pack marks an app version as broken, users see "Counting paused for Instagram, fix coming" instead of wrong numbers.

**Building and testing rules**

- Engineers find view IDs with Android Studio's Layout Inspector and `uiautomator dump`.
- We record real accessibility event streams with a hand-counted reel total, then replay them in unit tests. Every rule change must pass every saved recording.
- Accuracy target: 97% or better against a manual count.

**Breakage alarm:** phones send one anonymous daily signal per app version: "player seen" and "reels counted", numbers only. Player seen but almost no reels counted means a new Instagram or YouTube release broke a rule, and we get alerted within hours.

**Performance budget:** under 2 ms per event on a mid-range phone and under 1% battery per day. The engine reads only the part of the screen tree it needs, never the whole tree on every event.

## Limits, blocking and unlocking

Past the limit, we cover only the Reels or Shorts screen with our own block page; the rest of Instagram and YouTube keeps working. The user decides how hard that block is to get past.

**Limit types**

- Daily reel limit, for all apps together or per app.
- Session cap: reels per sitting, reset after 15 minutes away.
- Schedules (Pro): no reels during set hours, such as 9 pm to 7 am.

**Before the block:** notifications at 50% and 80% of the limit, and the brain visibly worsens. At 100%, Pro users get the block and free users get a final nudge with an upgrade offer.

**How the block works**

1. The limit engine sees a new reel event past the limit.
2. The service draws a full-screen page using the accessibility overlay window type, which needs no extra "display over other apps" permission.
3. The page shows the brain, today's count and the unlock options. "Leave" sends the user out of the Reels screen with a Back action.
4. If the user goes back into Reels, the page returns instantly.

**Strictness levels**

| Level | How to get past the block | Best for |
| --- | --- | --- |
| Gentle | Wait 10 seconds, then continue for 5 more reels | People who mainly want awareness |
| Firm | Finish a short task (type a sentence, or 60 seconds of breathing); 3 unlocks a day | Most users |
| Locked | No unlock until the scheduled time or the day ends | People who want a hard stop, like BrainPal's old timer lock |

**Making Locked mode credible:** Android lets users turn off any accessibility service or uninstall any app, and we will not use device-admin tricks to stop them. Instead:

- Loosening Locked mode takes effect only after 24 hours, a simple commitment device.
- If the service is turned off during a lock, the app records it, and Battles show "tracking was off" for that day.
- In V2, a friend can be required to approve an early unlock.

## Brain visual, UX and design

The brain is our mascot and main retention tool: it reacts to every reel, scales to each user's own limit, and heals when they do well.

**The brain**

- One Rive character with a state machine, driven by a single input: rot level, 0 to 100.
- Rot level = today's reels ÷ the user's daily limit × 100, capped at 100.
- Five stages: Fresh (0–20), Foggy (21–40), Cracking (41–60), Melting (61–80), Rotten (81–100).
- Small reactions bring it alive: a wince on each new reel, a yawn late at night, a glow on streak days.
- It heals overnight, and faster after a day under the limit. Reward matters as much as warning.
- Tone: funny and a little dramatic, never preachy. Copy reads like a friend teasing you.

**Key screens**

1. **Onboarding:** the promise, a plain-language permission explanation, a 20-second video of turning it on, brand-specific steps, then a live test: "Open Reels and scroll 3 times."
2. **Home:** the brain, today's count, a limit bar, the split by app, and a counter health dot.
3. **History:** daily bar chart, best day and current streak.
4. **Limits:** daily limit, session cap, schedules and strictness.
5. **Battles:** friends, today's standings and past results.
6. **Settings and paywall.**

**Onboarding decides everything.** Most drop-off happens at the permission step. The live 3-reel test proves the counter works before the user leaves, which is our first "wow" moment. Target: 60% of installs finish setup.

**Design system**

- Material 3 base with our own colour, type and shape tokens. Dark mode first, since much scrolling happens in bed.
- Smooth 60 fps motion and a light haptic tap at each milestone.
- Our own app fully supports TalkBack and large text.
- Budgets: cold start under 1 second, download size under 15 MB.

## Scroll Battles and social backend

Battles compare daily totals only; no reel content ever leaves the phone. Lower count wins, and a day with the counter switched off cannot win.

**Accounts:** the counter works with no account. Google sign-in is asked for only when the user joins Battles.

**Data model (Firestore)**

| Collection | Holds | Who can write |
| --- | --- | --- |
| `users/{uid}` | Display name, avatar, time zone, friend code | The user |
| `users/{uid}/days/{date}` | Total reels, per-app totals, minutes the counter was on | The user's phone |
| `friendships/{pairId}` | The two users and the request status | Both users |
| `battles/{battleId}` | Members, start, end, type, status | Cloud Functions |
| `battles/{battleId}/results/{uid}` | Final count and rank | Cloud Functions only |

**How a daily battle runs**

1. Phones upload today's totals after every 10 reels, and at least every 15 minutes, while a battle is active.
2. Friends see near-live standings in the app.
3. A scheduled Cloud Function closes the battle at midnight in the battle's time zone, writes results and sends a push notification.

**Adding friends:** a share link on our own domain using Android App Links, with the Play Install Referrer carrying the invite code through a fresh install. Friend codes work as a backup. We avoid Firebase Dynamic Links because [Google shut it down on Aug 25, 2025](https://www.courier.com/blog/firebase-dynamic-links-are-shutting-down-whats-next-for-notifications).

**Fair play**

- Each day's upload includes how long the counter was on. Days with more than 1 hour off between 8 am and midnight get a "tracking was off" badge and cannot win.
- Firestore security rules let users write only their own documents; only Cloud Functions write results.
- The server rejects impossible totals, such as a jump of hundreds of reels in a few minutes.

## Monetization and Pro

A freemium subscription with no ads: counting and the brain are free forever, while blocking and control are Pro.

| Feature | Free | Pro |
| --- | --- | --- |
| Reel counter, brain, widget | Yes | Yes |
| History | 7 days | Unlimited |
| Daily limit | Nudges only | Nudges and blocking |
| Strictness levels, schedules, session cap, per-app limits | No | Yes |
| Battles | 1 active battle | Unlimited, plus group battles |

**Pricing to test:** an annual plan with a 7-day free trial as the default, plus a monthly plan. Start with US prices around $4.99 a month and $29.99 a year, then set local prices per country, with a lower tier in price-sensitive markets such as India. RevenueCat experiments tell us which price wins.

**When the paywall appears**

1. Softly at the end of onboarding, with the trial offer.
2. The first time the user passes their limit: "Want this blocked next time?" This is the highest-intent moment.
3. When the user starts a second battle.

**Rules we keep:** clear cancel instructions, no fake countdown timers, and a reminder 2 days before a trial turns paid. Honest billing protects ratings, which drive installs.

**Targets:** 5% of installs start a trial, and 35% of trials convert to paid.

## Privacy, security and Play policy

Google Play allows our use of the Accessibility API, but only with an honest declaration, a clear in-app disclosure and the user's active consent ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)). This is a launch blocker, so we design for it from day one.

**Play policy checklist**

- [ ] Keep `isAccessibilityTool` false. [Play rejects non-tool apps that set it to true](https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data).
- [ ] Complete the Accessibility declaration in Play Console. Our case: counting individual reels requires knowing which screen is showing, and usage-stats APIs only give time per app. [Play expects narrower APIs wherever they can do the job](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
- [ ] Show a prominent disclosure before the settings step: what we read (which Instagram or YouTube screen is showing), what we never read or store (messages, captions, usernames, other apps), and two clear choices, "Agree" and "Not now".
- [ ] Record a short video of the disclosure flow and the counting feature for the reviewer.
- [ ] Explain the Accessibility use in the store listing, as the policy requires.
- [ ] Fill in the Data safety form to match reality exactly: account details and daily totals for Battles, anonymous diagnostics, nothing else.
- [ ] Offer account deletion both in the app and on the web.
- [ ] Publish a privacy policy on our website and link it from the app and the listing.
- [ ] Register the Play developer account as an organisation. [New personal accounts must first run a 14-day closed test with at least 12 testers](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

**Privacy architecture**

- No screen content is ever stored or sent; reel fingerprints live in memory only.
- Reel events stay on the phone for 90 days; after that, only daily totals are kept.
- The cloud receives daily totals, and only from signed-in Battle users.
- HTTPS everywhere, strict Firestore security rules, and no ad or tracking SDKs.

**Platform limits to plan for**

- [On Android 17, Advanced Protection mode revokes accessibility access from apps that are not accessibility tools](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860). We detect the mode and explain plainly why counting cannot run.
- Android lets apps mark views as sensitive, which hides them from non-tool services. If Instagram or YouTube did this on their players, detection would fail; see Risks.

## Reliability across phone brands

Many phone makers kill background services to save battery, a likely cause of BrainPal's "counter keeps stopping" reviews. We treat counter uptime as a feature, with a target of 99% of the time the phone is in use.

**What we build**

1. **Health check:** on every app open and every 15 minutes (WorkManager), compare the system's "service enabled" setting with whether our service actually connected. "Enabled but not running" is the classic failure.
2. **One-tap repair:** if counting stopped, a notification says "Counting stopped. Tap to fix" and opens the exact settings screen. The home screen health dot turns red.
3. **Crash-proof service:** every event handler is wrapped so no error can crash the service; errors go to Crashlytics. A crashing service can be switched off by the system or the user.
4. **Brand-specific setup:** onboarding detects the phone maker and shows the exact extra steps, using the [Don't Kill My App](https://dontkillmyapp.com) guides as a starting point.
5. **Battery setting:** we send users to the system screen to set our app to "Unrestricted", rather than using the direct-request permission that Play limits to special cases.
6. **Stay light:** no wake locks and no polling; everything is event-driven.

**Extra setup by brand** (menu names change between OS versions; confirm on our test phones)

| Brand | What goes wrong | Step we guide |
| --- | --- | --- |
| Xiaomi, Redmi, POCO | Background apps killed; Autostart off by default | Turn on Autostart; set battery saver to "No restrictions" |
| Samsung | Unused apps put to sleep | Add the app to "Never sleeping apps"; battery "Unrestricted" |
| Oppo, Realme, OnePlus | Background activity and auto-launch limited | Allow auto-launch and background activity |
| Vivo, iQOO | High background power use blocked | Allow high background power use |
| Google Pixel | Usually reliable | Battery "Unrestricted" only |

**Test phones to own:** at least one current and one older model from each brand above, 10 phones in total, covering Android 9 to 17.

## iOS strategy

iPhones cannot count reels, because Apple never lets an app read another app's screen. We launch iOS in months 7 to 10 as a time-based version that shares the brain, accounts and Battles.

**What Apple's Screen Time API allows**

- **FamilyControls:** the user picks the apps to watch, such as Instagram and YouTube, on their own phone.
- **DeviceActivity:** our extension is told when time thresholds are crossed, for example every 5 minutes in those apps. That drives the brain.
- **ManagedSettings:** when the limit is hit, a shield blocks the chosen apps; our custom shield screen shows the brain.
- **DeviceActivityReport:** usage charts inside our app.

**What it cannot do:** count individual reels, or block only the Reels tab. It blocks the whole app.

**How we build it**

- Swift and SwiftUI, because the Screen Time extensions must be native code.
- The same Rive brain file, Firebase backend and RevenueCat setup.
- Rot level on iOS = minutes in chosen apps ÷ the user's time limit.
- Apply early for Apple's Family Controls distribution entitlement; Apple must approve it before App Store release.
- One iOS engineer for about 3 months.

**Mixed Battles:** friends compare brain health (the share of their own limit used), not raw counts. That lets an iPhone user measured in minutes battle an Android user measured in reels.

## Analytics, testing and QA

We measure whether people actually scroll less, and we test counting accuracy as strictly as payments.

**Metrics and targets**

| Metric | Target | Why it matters |
| --- | --- | --- |
| Setup completed (permission granted in onboarding) | 60% of installs | The biggest drop-off point |
| Counting accuracy against a manual count | 97% or better | Wrong counts destroy trust |
| Counter uptime while the phone is in use | 99% | BrainPal's top complaint |
| Daily reels, week 4 compared with week 1 | 40% lower | Proves the product works |
| Day-30 retention | 20% or better | Habit apps live or die here |
| Trial-to-paid conversion | 35% | Funds the business |
| Crash-free users | 99.5% or better | Play ranks stable apps higher |

Analytics events carry names and numbers only, such as "limit\_reached, count=40", never screen content.

**Test layers**

1. **Unit tests:** detection rules replayed against recorded event streams, plus the limit engine and brain math. Tools: JUnit 5, MockK and Turbine for Flow.
2. **UI tests:** Compose UI tests and Roborazzi screenshot tests for every screen in light and dark mode.
3. **Real-phone tests:** a scripted "scroll 50 reels" run in the real Instagram and YouTube apps on all 10 test phones, compared with our count. Run it every release and every week, because those apps update often.
4. **Closed beta:** 100+ real users on Play's closed testing track for 3 weeks, with a "report wrong count" button in the app.
5. **Staged rollout:** 5%, then 20%, 50% and 100% of users, pausing if crashes or accuracy get worse.

## Team, roadmap and budget

We launch publicly in week 16 with a core team of five, and bring the iPhone app in months 7 to 10. Each phase ends at a gate, and we move on only when its numbers are met.

&#91;embedded content: Roadmap · 5 phases, 4 gates\]

The launch gate matters most: we never ship a counter people cannot trust.

**Team**

| Role | Time | Owns |
| --- | --- | --- |
| Senior Android engineer | Full-time | Detection engine, accessibility service, reliability |
| Android engineer | Full-time | Screens, widget, billing, onboarding |
| Backend engineer | Half-time | Firebase, Cloud Functions, rules-pack pipeline |
| Product designer + Rive animator | Half-time; animator on contract | Design system and the brain character |
| QA tester | Half-time from week 8 | Test-phone matrix and weekly real-app runs |
| Founder / product lead | Full-time | Scope, Play policy, beta community, growth |
| iOS engineer | From month 7 | The iPhone app |

**Effort:** about 12 person-months of engineering, design and QA to reach public launch.

**Running costs** (USD, excluding salaries, which depend on your location)

| Item | Cost |
| --- | --- |
| Google Play developer account | $25, one-time |
| Apple Developer Program, from month 6 | $99 a year |
| Firebase | Free tier at first; budget roughly $50 to $200 a month in year one |
| RevenueCat | Free up to $2,500 monthly tracked revenue, then 1% |
| 10 test phones | Roughly $2,000 to $3,000, one-time |
| Domain, website, email | About $100 a year |

## Risks and mitigations

The biggest risks are platform rules we don't control, so we keep a time-based fallback mode ready from month one. Risks are listed from most to least severe.

| Risk | What we do about it |
| --- | --- |
| Play rejects or removes the app over its Accessibility use | Follow the policy checklist exactly, keep the service limited to supported apps, and keep the disclosure video and appeal text ready |
| An Instagram or YouTube update breaks detection | Hot-fix the rules pack within hours, catch breaks with the daily alarm, and show "paused" rather than wrong counts |
| Meta or Google mark their players as sensitive, hiding them from our service | Fall back to counting from window and scroll signals alone; as a last resort, switch that app to time-based tracking |
| Android restricts accessibility further, beyond Advanced Protection mode | Test every Android beta from day one; keep a time-based Android mode (usage-stats permission) ready to ship |
| Phone makers kill the service | Health check, one-tap repair and brand-specific setup (see Reliability) |
| Novelty fades after the first weeks | Battles, streaks, a brain that heals, and the weekly insight card |
| Brain "damage" messaging upsets vulnerable users | A tone guide that teases but never shames, and no medical claims about brain harm |
| Competitors copy our features | Our edge is fast rule updates, reliability and trust, which are hard to copy quickly |

## Sources

- [BrainPal: Quit Scrolling, Google Play listing](https://play.google.com/store/apps/details?id=com.brainrot.android)
- [Target API level requirements, Android Developers](https://developer.android.com/google/play/requirements/target-sdk)
- [Use of the AccessibilityService API, Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Permissions and APIs that access sensitive information, Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
- [App testing requirements for new personal accounts, Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Stop malware from snooping on your app data, Android Developers Blog](https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data)
- [Android 17 Beta 2 restricts accessibility apps in Advanced Protection, Android Authority](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860)
- [RevenueCat pricing](https://revenuecat.com/pricing)
- [Firebase Dynamic Links shutdown, Courier](https://www.courier.com/blog/firebase-dynamic-links-are-shutting-down-whats-next-for-notifications)
