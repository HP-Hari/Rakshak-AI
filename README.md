# Rakshak AI (रक्षक AI) - On-Device NPU-Accelerated Scam Defense & Smart Merchant Soundbox

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Edge AI](https://img.shields.io/badge/Edge%20AI-Gemma--2B%20%7C%20Phi--3%20%7C%20TFLite-FF6F00.svg?style=flat&logo=tensorflow)](https://ai.google.dev/gemma)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Rakshak AI** is an on-device, privacy-preserving threat intelligence & cognitive defense engine built for Android. Designed specifically to protect elderly and vulnerable citizens against **"Digital Arrest" extortion, Social Engineering, Bank OTP theft, Remote Access Trojans (RATs), and Acoustic Payment Spoofing**.

Rakshak AI leverages **On-Device Small Language Models (SLMs)** running on the device Neural Processing Unit (NPU) alongside local TensorFlow Lite acoustic classifiers to provide zero-latency, 100% offline protection with no cloud transmission of private user data.

---

## 🌟 Key Highlights & Architectural Pillars

### 1. 🧠 On-Device SLM (Gemma-2B / Phi-3 / Sarvam) Semantic Guardian
* **Hardware-Accelerated NPU Pipeline:** Evaluates screen text and incoming SMS messages in ~200ms using quantized INT4 Small Language Models.
* **Contextual Coercion Recognition:** Replaces brittle keyword and regex filters with deep semantic understanding of urgent legal threats (e.g., fake CBI/Police warrants, impending power disconnection, courier seized packages).
* **100% Offline & Private:** Operates entirely within the device sandbox—no private conversations, screens, or messages leave the handset.

### 2. 🛡️ Active Call OTP & "Digital Arrest" Family Co-Pilot (SOS Mode)
* **Active-Call Correlation:** Cross-correlates incoming banking OTPs with active off-hook phone call states via `TelephonyManager`.
* **Loud Guardian (TTS Interception):** Automatically speaks high-priority voice warnings over the speakerphone if high-risk OTPs arrive during calls.
* **Automated Family SOS Alert:** Dispatches an emergency alert SMS with context to a trusted contact when high-coercion patterns or remote-access screen shares (AnyDesk/TeamViewer) are detected during unknown calls.

### 3. 🎙️ Edge TFLite Acoustic Spoof & Voice Clone Classifier
* **Spectral Analysis (Log-Mel Spectrogram):** Converts incoming audio streams to 40-bin Mel spectrograms to compute spectral centroid, spectral flatness, and pitch jitter.
* **Synthetic Voice & Clone Detection:** Flags AI-generated voices, robotic voice changers, and pressurized voice frequency signatures locally on device.

### 4. 🏪 Smart Vyapar Kirana Soundbox & Anti-Spoof Ledger
* **Acoustic Fraud Shield:** Prevents fake payment screenshot and fake UPI soundbox announcement scams by verifying audio acoustic signatures.
* **Local Soundbox & Ledger:** On-device Text-to-Speech multi-language payment announcements and Room-backed Udhar (Khata) credit ledger.

---

## 🏗️ System Architecture

```
                                +-----------------------------------+
                                |      Incoming Call / SMS / Screen  |
                                +-----------------+-----------------+
                                                  |
                         +------------------------+------------------------+
                         |                                                 |
                         v                                                 v
           +---------------------------+                     +---------------------------+
           | Telephony & SMS Receivers |                     |  Screen Content Extractor |
           |  (CallState & OTP Checks) |                     |   (Accessibility Service) |
           +-------------+-------------+                     +-------------+-------------+
                         |                                                 |
                         +------------------------+------------------------+
                                                  |
                                                  v
                               +-------------------------------------+
                               |   On-Device NPU Inference Engine    |
                               |    - Gemma-2B / Phi-3 SLM (INT4)    |
                               |    - TensorFlow Lite NNAPI          |
                               +------------------+------------------+
                                                  |
                         +------------------------+------------------------+
                         |                                                 |
                         v                                                 v
           +---------------------------+                     +---------------------------+
           |   Local TTS Audio Alarm   |                     |  Family Guardian SOS SMS  |
           | ("Never share your OTP!") |                     |  (Emergency Alert System) |
           +---------------------------+                     +---------------------------+
```

---

## 🛠️ Technology Stack

| Layer | Component | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0+ | Modern Android development with Coroutines & StateFlow |
| **UI Framework** | Jetpack Compose + Material 3 | Clean minimal interface with dynamic state observation |
| **Edge AI / SLM** | Gemma-2B / Phi-3 (INT4) | Small Language Models for contextual semantic threat analysis |
| **Edge ML** | TensorFlow Lite + NNAPI | Hardware-accelerated acoustic & signal feature extraction |
| **Cloud AI (Hybrid)** | Google Gemini 3.5 Flash | Optional deep psychological breakdown & merchant parsing |
| **Audio Engine** | Android TTS & AudioRecord | Local multi-language speech output & acoustic stream processing |
| **Persistence** | Jetpack Room | Fast local SQLite database for threat logs & Kirana ledger |

---

## 📂 Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Application Entry Point & Navigation
├── RakshakApplication.kt            # App Singleton & Lifecycle Initialization
├── audio/                           # Audio capture and frequency analysis utilities
├── data/                            # Room database, DAOs, and repository entities
├── engine/                          # AI & Core Detection Engines
│   ├── NpuInferenceEngine.kt        # On-Device SLM (Gemma/Phi) & NPU inference pipeline
│   ├── AudioScamTfLiteClassifier.kt # TFLite acoustic signal processing & spectrograms
│   ├── AcousticSpoofClassifier.kt   # High-speed DSP acoustic payment verification
│   ├── GeminiAiService.kt           # Hybrid Gemini 3.5 Flash intelligence service
│   ├── LiveSpeechRecognizerManager.kt # Real-time speech-to-text coordinator
│   ├── LocalTtsManager.kt           # Offline Text-to-Speech audio feedback engine
│   └── ScreenShareDetector.kt       # Remote desktop (RAT) app detection
├── service/                         # Android Background & System Services
│   ├── RakshakCallScreeningService.kt # Telephony pre-ring screening service
│   ├── SmsBroadcastReceiver.kt      # SMS spam filtering, OTP detection & Family SOS
│   └── AudioStreamService.kt        # Foreground acoustic stream processor
└── ui/                              # Jetpack Compose Screens & Material 3 Theme
    ├── RakshakApp.kt                # Top-level scaffold and navigation bars
    ├── screens/
    │   ├── guardian/CallGuardianScreen.kt  # Scam defense dashboard & live sentry
    │   ├── vyapar/SmartVyaparScreen.kt     # Merchant soundbox and khata ledger
    │   └── developer/DevServerSheet.kt     # Diagnostics & NPU hardware benchmarks
    └── theme/                       # Color palette, Typography, and Theme definition
```

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Ladybug | 2024.2.1 or newer
* Android SDK 36 (Android 15+)
* Minimum SDK: API 24 (Android 7.0)
* Device with NPU (Snapdragon 8 Gen series, Google Tensor G3/G4, or MediaTek Dimensity 9000+) recommended for optimal INT4 latency.

### Building & Running
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/rakshak-ai.git
   cd rakshak-ai
   ```
2. Set up environment configuration:
   ```bash
   cp .env.example .env
   # Add your optional GEMINI_API_KEY for hybrid cloud features
   ```
3. Build the debug APK via Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔒 Privacy & Safety Commitment
Rakshak AI is built strictly adhering to privacy-first edge design principles. All call metadata, SMS bodies, and screen representations used by the on-device SLM stay strictly on the local handset and are processed ephemerally in RAM/NPU registers.

---

## 📜 License
This project is licensed under the Apache 2.0 License.
