# DIAL

Web radio stream player in the Field Design Language. Signal accent: violet `#6200EA`.

- Playback via framework `MediaPlayer` (HTTP/ICY streams, `prepareAsync`).
- Stations persist as JSON in `SharedPreferences` (`org.json`, framework only).
- Station search via the [Radio Browser](https://www.radio-browser.info/) catalog API
  (`*.api.radio-browser.info`, public-domain data): type a term, press GO, tap a result to save it.
- Auto-retry re-tunes 3 s after a stream error when RETRY is on.
- UI is Jetpack Compose foundation only (`BasicText`, `BasicTextField`), styled per Field tokens.

## Build

Requires JDK 17 and an Android SDK (API 35). The Gradle wrapper jar is not committed;
generate it once with a local Gradle 8.11+ install:

```sh
gradle wrapper
./gradlew :app:assembleDebug
```

`minSdk` 24, `targetSdk` 35. Plain `http://` stream URLs are permitted via
`android:usesCleartextTraffic="true"`; prefer `https://` stations.

## Licenses

DIAL is licensed under the [Apache License 2.0](LICENSE) — see also [NOTICE](NOTICE).
All bundled libraries (AndroidX, Kotlin, kotlinx-coroutines) and the build tooling
(AGP, Kotlin Gradle plugins, Gradle) are Apache-2.0 as well; the full inventory is in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Station search data comes from
radio-browser.info and is public domain (CC0).
