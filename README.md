

# REAL or NOT

REAL or NOT is an Android application built with **Jetpack Compose** that analyzes media files and attempts to detect whether they are **real** or **AI-generated / manipulated**.  
The app supports scanning different media types, shows analysis progress and results, and stores scan history locally for later review.

## Features

- Scan media files and analyze authenticity
- Support for multiple media types:
  - Image
  - Video
  - Audio
- Show scan progress during analysis
- Display result details such as:
  - Verdict
  - Confidence
  - Processing time
  - Model used
  - Reasoning / explanation
- Save scan history locally using Room database
- API key setup flow for analyzer/service configuration
- Modern UI built with Jetpack Compose

## Project Architecture

This project follows an **MVVM architecture** with a layered structure:

- **UI Layer**
  - Built using Jetpack Compose
  - Handles screens, navigation, and user interaction

- **ViewModel Layer**
  - Manages UI state using `StateFlow` / `Flow`
  - Coordinates scan execution and persistence

- **Data Layer**
  - Uses Room database for storing scan history
  - Handles DAO and local persistence

- **Model Layer**
  - Contains entities, enums, and app data models

- **Inference Layer**
  - Performs deepfake / authenticity analysis
  - Returns analysis result and progress updates

## Package Structure

```text
composeApp/src/main/java/com/realornot
├── data        # Room database, DAO, local storage, API key manager
├── inference   # Deepfake analysis logic / inference services
├── model       # Data models, entities, enums
├── theme       # Compose theme, colors, typography
├── ui          # Screens and UI components
├── viewmodel   # ViewModel and UI state management
├── App.kt      # Root composable and navigation logic
└── MainActivity.kt  # Android entry point

Tech Stack

Language: Kotlin

UI: Jetpack Compose

Architecture: MVVM

Database: Room

State Management: StateFlow / Flow

Async Processing: Kotlin Coroutines


App Flow

1. App launches into splash screen


2. User configures API key if required


3. User selects a media file


4. Scan starts and progress is shown


5. Analysis result is displayed


6. Result is stored in local history



Data Stored

Each scan result may include:

File name

Media type

Final verdict

Confidence score

Model used

Processing time

Optional reasoning

Optional video/audio-specific verdicts and confidence


Setup Instructions

Prerequisites

Android Studio

Kotlin support

Android SDK

Gradle


Steps

1. Clone the repository:



git clone https://github.com/notg0d/p1.git

2. Open the project in Android Studio


3. Sync Gradle dependencies


4. Add any required API key or local configuration if your analyzer depends on external services


5. Run the app on an emulator or Android device



Build

To build the project:

./gradlew build

To install on a connected device:

./gradlew installDebug

Possible Improvements

Introduce repository pattern for better separation of concerns

Add dependency injection (Hilt / Koin)

Improve test coverage

Add export/share scan report

Improve API key security using encrypted storage

Replace manual navigation with Navigation Compose if project grows

Add better error reporting and analytics


Current Status

This project is a functional prototype / application demonstrating:

Compose UI

MVVM state management

Local persistence with Room

Media authenticity analysis workflow


Disclaimer

The analysis result provided by this app should be treated as an assistance tool, not absolute proof.
Detection accuracy depends on the underlying model/service and may produce false positives or false negatives.









