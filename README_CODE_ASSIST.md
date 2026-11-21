# NeonWave — Pure Java All-Format Audio Player

## Project Overview
- **Language:** 100% Java 11 (`app/src/main/java/com/example/`)
- **Audio Engine:** Jetpack Media3 (`media3-exoplayer` + `media3-session`) supporting MP3, FLAC, WAV, AAC, M4A, OGG, OPUS, AMR, 3GP, and MKA.
- **Audio FX:** `Equalizer` (10-band interpolation), `BassBoost`, `Virtualizer`, `LoudnessEnhancer`, and live `Visualizer` FFT capture.
- **UI:** Material 3 Dark Neon theme with custom `NeonWaveformView` (3 modes) and interactive 10-band `NeonEqualizerView`.

## Exporting & Building in Code Assist (Android)
1. Export the full project ZIP from the platform menu and extract it on your phone.
2. Open the extracted folder in **Code Assist** (supports Gradle + Java 11 + AndroidX).
3. Ensure SDK Platform **36** (or update `compileSdk` in `app/build.gradle.kts` to match your installed Android SDK in Code Assist if needed) and build the Debug APK.
4. All permissions (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `MODIFY_AUDIO_SETTINGS`, `RECORD_AUDIO`) are pre-configured in `AndroidManifest.xml` and requested safely at runtime.
