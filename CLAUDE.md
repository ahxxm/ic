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

## Progress Tracking

After completing phase work:
1. Update `IMPL_PLAN.md` - mark completed items `[x]`, update "Current Focus" section
2. Verify build passes before marking phase complete

## Reference

This file follows [Writing a Good CLAUDE.md](https://www.humanlayer.dev/blog/writing-a-good-claude-md): minimal, pointers over copies, no style rules.
