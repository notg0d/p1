<p align="center">
  <h1 align="center">🛡️ REALorNOT</h1>
  <p align="center"><strong>AI-Powered Deepfake Detection for Android</strong></p>
  <p align="center">
    Detect AI-generated images, videos, and audio using Google's Gemini 2.5 Flash model
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/AI-Gemini%202.5%20Flash-orange?logo=google" />
</p>

---

## 📖 About

**REALorNOT** is a native Android application that detects AI-generated and manipulated media — including deepfake images, face-swapped videos, and voice-cloned audio — using Google's **Gemini 2.5 Flash** multimodal AI model.

The app performs **multi-layer forensic analysis** by sending media files to the Gemini API with carefully engineered forensic prompts, providing users with a verdict (**REAL** or **AI-GENERATED**), a confidence score, and a detailed reasoning explanation.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🖼️ **Image Detection** | Detects AI-generated images from Stable Diffusion, Midjourney, DALL-E, and face-swap tools |
| 🎬 **Video Detection** | Dual-track analysis — visual frame analysis + audio track analysis |
| 🔊 **Audio Detection** | Identifies voice cloning from ElevenLabs, Bark, RVC, XTTS, and other TTS engines |
| 📊 **Confidence Scoring** | 0–100% confidence score with detailed forensic reasoning |
| 📜 **Scan History** | All past scans saved locally with Room database |
| 🔐 **Secure API Key Storage** | AES-256 encrypted key storage via Android EncryptedSharedPreferences |

---


---

## 🚀 Getting Started

### Prerequisites

- Android device running **Android 8.0 (API 26)** or higher
- A **Google Gemini API key** (free tier available)

### Installation

#### Option 1: Install from APK
1. Download the latest APK from the [Releases](../../releases) section
2. Transfer the APK to your Android device
3. Open the APK → Allow "Install from unknown sources" when prompted
4. Launch **REALorNOT**

#### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/notg0d/REALorNOT.git
cd p1

# Build the release APK
./gradlew assembleRelease

# APK location:
# composeApp/build/outputs/apk/release/composeApp-release.apk
```

### Setup

1. **Get a Gemini API Key:**
   - Visit [Google AI Studio](https://aistudio.google.com/apikey)
   - Click **"Create API key"**
   - Copy the generated key

2. **First Launch:**
   - Open the app → You'll see the welcome screen
   - Paste your API key → Tap **"Get Started"**
   - The key is encrypted and stored securely on your device

3. **Start Scanning:**
   - Tap **"Start Scanning"** on the dashboard
   - Select an image, video, or audio file from your device
   - Wait for the AI analysis to complete
   - View the verdict, confidence score, and forensic reasoning

---

## 🏗️ System Architecture

```
┌────────────────────────────────────────────────────────┐
│                    REALorNOT App                       │
│                                                        │
│  ┌──────────┐   ┌──────────────┐   ┌───────────────┐   │
│  │  UI Layer │──▶│ ViewModel    │──▶│   Inference │   │
│  │ (Compose) │   │ (ScanState)  │   │   Engine     │   │
│  └──────────┘   └──────────────┘   └───────┬───────┘   │
│        │                                    │          │
│        │         ┌──────────────┐           │          │
│        │         │  Room DB     │           │          │
│        └────────▶│  (History)   │           │         │
│                  └──────────────┘           │          │
│                                             ▼          │
│                                   ┌─────────────────┐  │
│                                   │  API Key Manager│  │
│                                   │  (AES-256)      │  │
│                                   └────────┬────────┘  │
└────────────────────────────────────────────┼───────────┘
                                             │
                                             ▼
                                  ┌────────────────────┐
                                  │  Google Gemini API  │
                                  │  (gemini-2.5-flash) │
                                  │                     │
                                  │  Multimodal AI:     │
                                  │  • Image analysis   │
                                  │  • Video frames     │
                                  │  • Audio analysis   │
                                  └────────────────────┘
```

---

## 🔍 How Detection Works

### Image Analysis

```
User selects image
        │
        ▼
  Load full-resolution bitmap
        │
        ▼
  Send to Gemini 2.5 Flash with forensic prompt
        │
        ▼
  AI performs multi-layer analysis:
  ├── Facial analysis (skin pores, iris symmetry, teeth, hair)
  ├── Structural analysis (hands, fingers, body proportions)
  ├── Pixel-level analysis (JPEG artifacts, noise patterns)
  └── Generator signatures (SD, Midjourney, DALL-E, DeepFaceLab)
        │
        ▼
  Returns JSON: { verdict, confidence, reasoning }
```

**Forensic Checks Include:**
- **Skin texture**: Real skin has pores, micro-blemishes — AI skin is often too smooth
- **Eye analysis**: Iris symmetry, corneal reflections, pupil shape consistency
- **Hands & fingers**: AI commonly generates 6 fingers or fused digits
- **Text in images**: AI frequently produces garbled or nonsensical text
- **GAN fingerprints**: Spectral artifacts, checkerboard patterns in frequency domain

### Video Analysis (Dual-Track)

```
User selects video
        │
        ▼
  ┌─────────────────────────────────────────┐
  │         PARALLEL ANALYSIS               │
  │                                         │
  │  Track 1: VISUAL                        │
  │  ├── Extract 16 evenly-spaced frames    │
  │  ├── Send all frames to Gemini          │
  │  └── Analyze temporal consistency:      │
  │      • Face identity drift              │
  │      • Lighting consistency             │
  │      • Blinking patterns                │
  │      • Face-neck boundary artifacts     │
  │      • Head pose vs face alignment      │
  │                                         │
  │  Track 2: AUDIO                         │
  │  ├── Read full video file bytes         │
  │  ├── Send as video/mp4 blob to Gemini   │
  │  └── Analyze audio for:                 │
  │      • Voice naturalness                │
  │      • Breathing patterns               │
  │      • Spectral artifacts               │
  │      • Voice cloning signatures         │
  └─────────────────────────────────────────┘
        │
        ▼
  Merge verdicts:
  IF either track = AI-GENERATED → final = AI-GENERATED
  Confidence = max(visual_conf, audio_conf)
```

### Audio Analysis

```
User selects audio file (WAV, MP3, M4A, etc.)
        │
        ▼
  Read file bytes + detect MIME type
        │
        ▼
  Send audio blob to Gemini with forensic prompt
        │
        ▼
  AI analyzes:
  ├── Voice naturalness (breathing, micro-pauses, pitch)
  ├── Technical artifacts (spectral gaps, room acoustics)
  └── Voice cloning signatures:
      • ElevenLabs: metallic consonants
      • Bark/Tortoise: sibilant artifacts
      • RVC: source speaker bleed-through
      • XTTS: unnatural word boundaries
        │
        ▼
  Returns JSON: { verdict, confidence, reasoning }
```

---

## 📂 Project Structure

```
com.realornot/
├── App.kt                          # Root navigation composable
├── MainActivity.kt                 # Android entry point
│
├── data/
│   ├── ApiKeyManager.kt            # AES-256 encrypted API key storage
│   ├── AppDatabase.kt              # Room database singleton
│   └── ScanResultDao.kt            # Database queries (reactive Flows)
│
├── inference/
│   └── DeepfakeAnalyzer.kt         # Gemini API forensic analysis engine
│                                    #   - IMAGE_PROMPT: facial/structural/pixel analysis
│                                    #   - VIDEO_VISUAL_PROMPT: temporal consistency checks
│                                    #   - AUDIO_FORENSIC_PROMPT: voice cloning detection
│                                    #   - parseGeminiResponse(): JSON extraction + fallback
│
├── model/
│   ├── MediaType.kt                # IMAGE/VIDEO/AUDIO enum with MIME routing
│   └── ScanResult.kt               # Room entity (verdict, confidence, reasoning)
│
├── theme/
│   ├── Color.kt                    # Sage-green dark palette
│   ├── Theme.kt                    # Material3 dark color scheme
│   └── Type.kt                     # Space Grotesk + DM Sans typography
│
├── ui/
│   ├── ApiKeyScreen.kt             # API key input with encrypted storage
│   ├── DashboardScreen.kt          # Home screen with file picker
│   ├── HistoryScreen.kt            # Past scan results (LazyColumn)
│   ├── ScanningScreen.kt           # Animated progress + step checklist
│   ├── ScanResultScreen.kt         # Verdict display with media preview
│   ├── SplashScreen.kt             # Boot animation (spring physics)
│   └── components/
│       ├── AnimatedProgress.kt     # Determinate + indeterminate bars
│       └── FluidTabBar.kt          # Gooey-physics navigation bar
│
└── viewmodel/
    └── ScanViewModel.kt            # Scan orchestration + state management
```

---

## 🛡️ Security

| Aspect | Implementation |
|--------|---------------|
| **API Key Storage** | AES-256-GCM encryption via `EncryptedSharedPreferences` |
| **Key Encryption** | Android Keystore-backed `MasterKey` (hardware-backed on supported devices) |
| **Network** | HTTPS-only communication with Google's Gemini API |
| **Data Privacy** | All scan history stored locally — no external telemetry |
| **Key Access** | Hidden behind a 5-tap secret on the History tab |

---

## ⚙️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.0 |
| **UI Framework** | Jetpack Compose (Material 3) |
| **AI Backend** | Google Gemini 2.5 Flash API |
| **Database** | Room (SQLite) with reactive Flows |
| **Architecture** | MVVM (ViewModel + StateFlow) |
| **Security** | AndroidX Security Crypto (AES-256) |
| **Typography** | Google Fonts (Space Grotesk, DM Sans) |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 15 (API 35) |

---

## 📋 API Configuration

The app uses **Google Gemini 2.5 Flash** with the following generation config:

```kotlin
temperature = 0.1f     // Highly deterministic (forensic precision)
topP = 0.5f            // Focused sampling
topK = 20              // Limited token candidates
candidateCount = 1     // Single response
```

Low temperature ensures consistent, repeatable forensic analysis rather than creative/varied outputs.

---

## 🗺️ App Flow

```
Launch
  │
  ▼
Splash Screen (2.5s animated boot)
  │
  ▼
Has API Key? ──NO──▶ API Key Setup Screen
  │                        │
  YES                  Enter key → Save
  │                        │
  ▼                        ▼
Dashboard ◀────────────────┘
  │
  ▼
"Start Scanning" → File Picker (image/video/audio)
  │
  ▼
Scanning Screen (progress + step checklist)
  │
  ├── Step 1: File uploaded
  ├── Step 2: Preprocessing
  ├── Step 3: Running AI analysis (Gemini API call)
  └── Step 4: Generating report
  │
  ▼
Result Screen
  ├── Media preview (image/video thumbnail/audio icon)
  ├── Verdict: REAL or AI-GENERATED
  ├── Confidence: 0-100%
  ├── Analysis details (file name, model, processing time)
  ├── Video: separate visual + audio verdicts
  └── AI reasoning (forensic explanation)
  │
  ▼
"New Scan" → Back to Dashboard
```

---

## 📄 License

This project is open source. Feel free to use, modify, and distribute.

---

## 👤 Author

**notg0d** — [github.com/notg0d](https://github.com/notg0d)
