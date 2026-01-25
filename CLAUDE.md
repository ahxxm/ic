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
```

## Reference

This file follows [Writing a Good CLAUDE.md](https://www.humanlayer.dev/blog/writing-a-good-claude-md): minimal, pointers over copies, no style rules.
