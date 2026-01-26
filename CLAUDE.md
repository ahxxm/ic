# Image Compressor

Native Android app for JPEG compression (jpegli, mozjpeg).

## Project Context

- `PROJECT.md` - architecture, decisions, resolved items
- `IMPL_PLAN.md` - phased implementation plan, current focus

## Stack

- Kotlin + Jetpack Compose
- NDK/CMake for native encoders (deferred - using Bitmap.compress initially)
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

Version uses single integer: `baseVersion` in app/build.gradle.kts must equal git tag number.
- baseVersion = 2 → tag v2 → versionName "2" → versionCodes 20-24

## Progress Tracking

After completing phase work:
1. Update `IMPL_PLAN.md` - mark completed items `[x]`, update "Current Focus" section
2. Verify build passes before marking phase complete

## Reference

This file follows [Writing a Good CLAUDE.md](https://www.humanlayer.dev/blog/writing-a-good-claude-md): minimal, pointers over copies, no style rules.
