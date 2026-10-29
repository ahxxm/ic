# Image Compressor

Native Android app for JPEG compression (jpegli, mozjpeg). iOS port (working, local dev) in `ios/`.

## Stack

- Kotlin + Jetpack Compose
- Target: Android 11+ (API 30)
- Distribution: F-Droid only

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease  # signed APK
```

## APK Testing

Upload signed arm64 APK to tmpfiles.org using a shorter name:
```bash
# mv variants to `app.apk` for a shorter url
curl -s -F "file=@app/build/outputs/apk/release/app.apk" https://tmpfiles.org/api/v1/upload
# direct download: replace tmpfiles.org with tmpfiles.org/dl
# our network is unstable, after upload, download and compare MD5 hash
```

## Release

To release: bump `baseVersion` in app/build.gradle.kts, tag the commit `v{baseVersion}.0`. baseVersion must equal git tag number.
- baseVersion = 5, tag v5.0 → versionName "5.0" → versionCodes 50-54

## Reference

This file follows [Writing a Good CLAUDE.md](https://www.humanlayer.dev/blog/writing-a-good-claude-md): minimal, pointers over copies, no style rules.
