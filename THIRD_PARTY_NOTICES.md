# Third-Party Notices

DIAL itself is licensed under the [Apache License 2.0](LICENSE).
The following third-party components are used. All of them are licensed under
the Apache License, Version 2.0 (<https://www.apache.org/licenses/LICENSE-2.0>),
so a single license text covers the whole dependency tree.

## Runtime dependencies (shipped in the APK)

| Component | Version | Copyright | License | Source |
|---|---|---|---|---|
| AndroidX Activity Compose (`androidx.activity:activity-compose`) | 1.9.3 | Google LLC / Android Open Source Project | Apache-2.0 | <https://developer.android.com/jetpack/androidx> |
| AndroidX Compose UI (`androidx.compose.ui:ui`) + transitive AndroidX libraries (runtime, core, lifecycle, annotation, …) | 1.7.6 | Google LLC / Android Open Source Project | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| AndroidX Compose Foundation (`androidx.compose.foundation:foundation`) | 1.7.6 | Google LLC / Android Open Source Project | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| Kotlin standard library | 2.0.21 | JetBrains s.r.o. and Kotlin contributors | Apache-2.0 | <https://github.com/JetBrains/kotlin/blob/master/license.txt> |
| kotlinx.coroutines (`kotlinx-coroutines-android`) | 1.9.0 | JetBrains s.r.o. and kotlinx.coroutines contributors | Apache-2.0 | <https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE> |
| Media3 ExoPlayer (`androidx.media3:media3-exoplayer` + `media3-exoplayer-hls`) — MP3/AAC-ADTS/HLS/ICY playback | 1.5.1 | Google LLC / Android Open Source Project | Apache-2.0 | <https://github.com/androidx/media3/blob/main/LICENSE> |

The Android framework classes used at runtime (`MediaPlayer`, `org.json`,
`SharedPreferences`, …) are part of the Android Open Source Project and are
licensed under the Apache License 2.0; they ship with the OS, not with this APK.

## Build-time tooling (not shipped in the APK)

| Component | Version | License | Source |
|---|---|---|---|
| Android Gradle Plugin (`com.android.application`) | 8.7.3 | Apache-2.0 | <https://developer.android.com/build/releases/gradle-plugin> |
| Kotlin Gradle Plugin + Compose compiler plugin (`org.jetbrains.kotlin.*`) | 2.0.21 | Apache-2.0 | <https://github.com/JetBrains/kotlin> |
| Gradle (wrapper distribution) | 8.11.1 | Apache-2.0 | <https://gradle.org/license/> |

## Data sources (accessed at runtime, not bundled)

| Source | Data license | Used for |
|---|---|---|
| [Radio Browser](https://www.radio-browser.info/) API (`*.api.radio-browser.info`) | Public domain (CC0) | Station search: names, stream URLs, metadata |

The station streams themselves (e.g. SomaFM, Radio Paradise) are operated by
their respective broadcasters; DIAL only plays the public stream URLs.
