<p align="center">
  <img src="docs/banner.svg" alt="IRIS AI for Android" width="100%" />
</p>

<h1 align="center">IRIS AI &middot; Android</h1>

<p align="center">
  <b>Offline-first Hinglish voice assistant for Android 10+</b><br/>
  Wake word &rarr; speech &rarr; on-device intents &rarr; real phone actions &rarr; neural voice reply
</p>

<p align="center">
  <img alt="min sdk" src="https://img.shields.io/badge/minSdk-29%20(Android%2010)-00ff41?style=flat-square" />
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-22d3ee?style=flat-square" />
  <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-22d3ee?style=flat-square" />
  <img alt="tools" src="https://img.shields.io/badge/tools-59-00ff41?style=flat-square" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square" />
</p>

---

## Kya hai ye?

Android port of the [IRIS-AI](https://github.com/IRISX-AI/IRIS-AI) desktop assistant &mdash; same glass/neon UI, same orb, but built as a real phone assistant. The UI, wake word, speech recognition, intent parsing, phone automation and now even the **voice** run on-device, so the app keeps working with mobile data off. The cloud brain is optional and only used for open-ended questions when you add an API key.

## Highlights

| | |
|---|---|
| **Live-call conversation** | Live mode: after every answer the mic reopens by itself. Say the wake word mid-sentence to cut IRIS off (barge-in). |
| **Real-time waveform orb** | 64 bars around the orb driven by actual mic amplitude, not a timer. |
| **Always in the background** | Foreground service with a *Bolo* action, restarts on task-swipe, comes back after reboot. |
| **No-app mic** | Quick Settings tile and home-screen widget open the mic directly, without launching the app. |
| **IRIS ki apni awaaz** | On-device neural TTS (VITS via ONNX Runtime). One-time ~30 MB download, then the same voice offline on every phone. |
| **Neural wake word** | openWakeWord (ONNX) &rarr; Vosk &rarr; system recogniser fallback chain. No beeps, works offline and online. |
| **Offline speech** | Vosk small en-IN model, downloaded on demand. |
| **Screen control** | Accessibility-driven tapping, typing, form filling, reading and replying to what is on screen. |
| **Floating bubble** | IRIS over every app, with live subtitle of what you say and what it answers. |
| **Camera + gallery vision** | ML Kit OCR and labelling, 3D-tilt gallery viewer, long-press photo &rarr; text to clipboard. |
| **Macros** | Record your own command sequences ("office mode") and pin them as home-screen shortcuts. |

## Voice commands (a taste)

```
"YouTube pe <song> chalao"          "gana pause karo" / "agla gana"
"kya baj raha hai?"                  "Riya ko photo bhejo"
"Papa ko location bhejo"             "Rahul ko whatsapp karo ki late ho jaunga"
"battery kitni hai?"                 "kitne baje hain?"
"torch on karo"                      "screenshot lelo"
"silent mode" / "DND on karo"        "bluetooth on karo" / "hotspot chalu karo"
"ye kya likha hai?"                  "ye form bhar do"
"joke sunao"                         "neural voice setup karo"
```

All 59 tools are registered in [`ToolRegistry.kt`](app/src/main/java/com/irisx/ai/core/agent/ToolRegistry.kt); the offline phrase routing lives in `LocalIntentParser.kt` and `ExtraIntentParser.kt`.

## First run

The app opens a **setup wizard** that walks through everything Android will not let an app grant itself:

1. **Accessibility** &mdash; screen control, auto-send in WhatsApp/Instagram.<br/>On Android 13/14 first do *App info &rarr; &#8942; &rarr; Allow restricted settings*.
2. **Notification access** &mdash; music control (MediaSession) and notification digests.
3. **Do Not Disturb access** &mdash; silent / DND commands.
4. **Display over other apps** &mdash; floating bubble + live subtitle.
5. **Battery optimisation off** &mdash; keeps the always-listening service alive.
6. **Exact alarms** &mdash; reminders and timers fire on time.
7. **Neural voice download** (~30 MB) &mdash; IRIS ki apni awaaz, offline after that.
8. **Neural wake word** &mdash; or say later: *"neural wake word setup karo"*.

You can reopen the wizard from **Settings &rarr; System access &rarr; Setup wizard dobara dikhao**.

## Models (downloaded on demand, never bundled)

| Model | Size | Used for | Source |
|---|---|---|---|
| VITS `en_US-ljspeech` | ~30 MB | neural voice output | HuggingFace `csukuangfj` |
| openWakeWord (mel + embedding + wake) | ~5 MB | wake word | openWakeWord v0.5.1 release |
| Vosk `small-en-in-0.4` | ~40 MB | offline speech to text | alphacephei.com |

Everything lands in the app's private `filesDir`, so uninstalling removes it all. Delete individual models from **Settings &rarr; Voice models**.

## Build

```bash
gradle assembleDebug        # or use the GitHub Actions workflow
```

CI (`.github/workflows/android.yml`) builds debug + release on every push to `main`, uploads `iris-ai-debug` as an artifact and publishes tag `v1.0.0` with both APKs.

To get a properly signed release, add these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the release APK is debug-signed, and Play Protect will warn on install (*Install anyway*).

## Honest limitations

- **Hotspot** has no public toggle API &mdash; IRIS opens the tether screen, you flip the switch. Bluetooth on Android 13+ shows a system dialog for the same reason.
- **WhatsApp live location** cannot be started by another app; IRIS sends a Google Maps link of your current location instead.
- **Auto-send** in WhatsApp / Instagram / Telegram needs Accessibility; without it the chat opens with the message pre-filled.
- **Screenshots** use the Accessibility screenshot action (Android 11+).
- The neural voice is English-trained; heavy Hindi sentences still fall back to the phone's TTS engine.

## Roadmap

- [x] Same UI/design as desktop IRIS-AI, Android 10+, offline-first
- [x] Automation: WhatsApp / Instagram / Telegram / camera / gallery
- [x] Themes, 3D gallery, OCR, macros, widget, tile, bubble
- [x] Neural wake word (openWakeWord)
- [x] Real-time waveform orb, always-on service, music control, everyday toggles
- [x] Live-call conversation with barge-in
- [x] On-device neural voice
- [x] First-run setup wizard, live subtitle in bubble
- [ ] Hindi neural voice model
- [ ] On-device translation for the bubble subtitle
- [ ] Play Store ready signed release

---

<p align="center"><sub>Ported and extended from <a href="https://github.com/IRISX-AI/IRIS-AI">IRISX-AI/IRIS-AI</a>. MIT licensed.</sub></p>
