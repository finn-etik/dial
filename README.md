# DIAL

Web radio stream player in the Field Design Language. Signal accent: violet `#6200EA`.

- Playback via framework `MediaPlayer` (HTTP/ICY streams, `prepareAsync`).
- Stations persist as JSON in `SharedPreferences` (`org.json`, framework only).
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
