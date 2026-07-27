# IRIS AI — Android (Offline-First Voice Layer)

Android-native rebuild of [IRISX-AI/IRIS-AI](https://github.com/IRISX-AI/IRIS-AI) (an Electron desktop app) as a real phone app — **same dark glassmorphic UI, same `#00ff41` accent, same AI-core orb + command dock**, but running natively on **Android 10+ (minSdk 29)** and working **without internet** for everything except optional cloud reasoning.

> Original desktop project by Harsh Pandey (IRISX-AI). Electron / React / Three.js can't run as a native Android app, so this is a **ground-up Kotlin + Jetpack Compose rebuild** of the same product design.

---

## Screens (same tab structure as desktop)

| Tab | Desktop equivalent | Works offline |
|---|---|---|
| **Command** | `Dashboard` — AI core sphere, telemetry rail, control dock, live feed | ✅ |
| **Notes** | `Notes` — local memory vault | ✅ |
| **Gallery** | `Gallery` — MediaStore image grid | ✅ |
| **Device** | `Phone` / `APP` — installed app index + battery/OS telemetry | ✅ |
| **Settings** | `Settings` — wake word, engine mode, API key, permissions | ✅ |

Design tokens kept 1:1 with the desktop app: black canvas, `bg-zinc-950/40` glass panels with `white/5` borders, 9–10sp mono uppercase micro-labels, emerald active tab pill, red disconnect/mute, cyan screen-vision mode.

---

## Architecture

```
com.irisx.ai
├─ ui/                 Compose UI (theme, components, dashboard, notes, gallery, device, settings)
│  └─ AssistantViewModel.kt   wake word -> STT -> agent -> tools -> TTS state machine
├─ core/
│  ├─ voice/           WakeWordEngine, SttEngine (EXTRA_PREFER_OFFLINE), TtsEngine
│  ├─ agent/           LocalIntentParser (Hinglish), ToolRegistry, LlmClient, AgentEngine
│  └─ tools/           apps, calls, SMS, WhatsApp, alarm, timer, torch, volume, media,
│                      notes, web search, screen control, notification reader
├─ service/            IrisForegroundService (always-on), Accessibility, NotificationListener, BootReceiver
├─ data/               SettingsStore, NotesStore, HistoryStore (SharedPreferences + org.json)
└─ util/               NetworkMonitor
```

**Decision order in `AgentEngine`:**
1. `LocalIntentParser` — offline Hinglish/English rules (instant, no network)
2. `LocalSmallTalk` — offline canned replies
3. Cloud LLM with function calling — **only if** online **and** an API key is set **and** `LOCAL ONLY` is off

---

## Offline vs online

**Works with zero internet:**
- Full UI, AI-core animation, tabs, notes, gallery, app index, telemetry
- Wake word ("hey iris") + speech-to-text via the on-device recognizer (`EXTRA_PREFER_OFFLINE`)
- On-device Text-to-Speech replies
- All device actions: open app, call, SMS, WhatsApp chat, alarm, timer, torch, volume, media keys, notes, screen back/home/recents/scroll, screen text reading, notification reading, battery/time/date, settings panels

**Needs internet (optional):**
- Open-ended questions / reasoning via an OpenAI-compatible endpoint (`Settings → Cloud Brain`)
- Web search tool (opens the browser)

> Tip: for true offline speech, install the offline voice pack — *Android Settings → System → Languages & input → Google Voice typing → Offline speech recognition* (English / Hindi).

---

## Voice commands (Hinglish + English)

```
hey iris → WhatsApp kholo
Mummy ko call karo
Rahul ko sms bhejo main aa raha hoon
7 baje ka alarm laga do
5 minute ka timer
torch on / torch band karo
volume badha do / mute karo
gana play karo / next
note karo kal 4 baje meeting hai
mere notes padho
battery kitni hai / time kya hai
notifications padho
screen padho
back jao / home jao / scroll karo
wifi kholo
google karo IPL score
```

---

## Permissions

Runtime: microphone, contacts, phone (call), SMS, media images, notifications.
Manual one-tap grants from **Settings → System Access**: Accessibility (screen control + reading), Notification access, Battery-optimisation exemption.

---

## Build

```bash
git clone https://github.com/coding-king-sk/IRIS-AI-Android.git
cd IRIS-AI-Android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio (Jellyfish+) and hit Run. A GitHub Actions workflow (`.github/workflows/android.yml`) also builds a debug APK on every push — download it from the run's **iris-ai-debug** artifact.

**Stack:** Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.09.03, minSdk 29 / targetSdk 34, OkHttp, Coil. No Room/KSP/DataStore — deliberately dependency-light.

---

## Setup after install

1. Grant mic + notification permissions on first launch.
2. Tap the green call button to bring the core online (starts the foreground wake-word service).
3. Optional: **Settings → Cloud Brain** → paste an API key for smarter answers. Leave blank to stay 100% local.
4. Optional: enable Accessibility for screen control.

---

## Credits

Product design, UI language and concept: **[IRISX-AI/IRIS-AI](https://github.com/IRISX-AI/IRIS-AI)** by Harsh Pandey.
Android implementation: this repository. Please respect the upstream project's license when redistributing.
