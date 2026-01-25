# Image Compressor (Android)

Minimalist native Android app for JPEG compression using jpegli and mozjpeg.

## Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Distribution | F-Droid only | No Play Store |
| Min Android | 11+ (API 30) | Trash API, better MediaStore, modern baseline |
| Backup strategy | None | Temp double during processing, user previews savings first, can skip images with no benefit |
| UI framework | Jetpack Compose | Code simplicity, single-activity |
| Storage access | MediaStore + createWriteRequest | Browse-first UX via MediaStore queries; single system dialog per batch for write access; no broad "all files" permission |
| Encoder integration | NDK build from source | F-Droid requires source builds, no prebuilt .so |
| Processing | Foreground service | Survives backgrounding, user sees progress |
| Format conversion | PNG only | HEIC/WebP already well-compressed, skip |
| EXIF | Checkbox, default ON | User toggle, preserve by default |
| Savings threshold | 10% AND 50KB | Auto-deselect if savings below both thresholds |
| Preview strategy | Random sample | One random image compressed on options pick; full results available post-compression |

## Architecture

```
app/
├── src/main/
│   ├── kotlin/
│   │   ├── ui/           # Compose screens
│   │   ├── domain/       # Compression logic, file operations
│   │   └── native/       # JNI bindings
│   └── cpp/
│       ├── jpegli/       # jpegli source (from libjxl)
│       ├── mozjpeg/      # mozjpeg source
│       └── compressor.cpp # JNI interface
```

### JNI Surface (minimal)

```kotlin
object NativeCompressor {
    external fun compress(
        inputPath: String,
        outputPath: String,
        quality: Int,
        encoder: Encoder  // JPEGLI or MOZJPEG
    ): CompressionResult
}

data class CompressionResult(
    val originalSize: Long,
    val compressedSize: Long,
    val success: Boolean,
    val error: String?
)
```

## User Flow

1. Grant READ_MEDIA_IMAGES permission (one-time)
2. App shows folders via MediaStore grouping (e.g., "DCIM/Camera - 2,847 images, 4.2GB")
3. **Options screen:**
   - Encoder toggle (jpegli/mozjpeg)
   - Quality slider (70-95)
   - EXIF preserve checkbox (default ON)
   - Convert PNG → JPEG checkbox (optional)
   - → Random image compressed as sample preview
4. **Savings preview screen:**
   - List sorted by savings KB desc
   - Images <10% AND <50KB savings auto-deselected (user can override)
   - Total estimated savings shown
   - Tap image → before/after comparison
5. Confirm → createWriteRequest system dialog → foreground service compresses with progress notification
6. Done → summary of space saved, all compressed versions viewable

## Resolved Items

- **Format handling**: Option to convert PNG → JPEG; skip HEIC/WebP (already well-compressed)
- **EXIF**: Checkbox, default preserve
- **Re-compression detection**: Preview stage shows savings ordered by KB desc; 10% AND 50KB threshold auto-deselects low-benefit images
- **Preview generation**: Random sample on options pick; all results available post-compression

## MVP Scope

Phase 1:
- Single folder selection
- Mozjpeg only (jpegli build is higher risk)
- Quality slider
- Override toggle
- Single-image before/after preview
- Batch compress with progress
- JPEG only, skip others

Phase 2:
- Jpegli encoder option
- Multi-folder management
- PNG → JPEG conversion (optional)
- Batch preview with savings estimate

## Build Notes

### Mozjpeg NDK
```cmake
# Known working approach - build turbojpeg component
add_subdirectory(mozjpeg)
target_link_libraries(compressor mozjpeg-static)
```

### Jpegli NDK (experimental)
Jpegli lives in libjxl repo. Building standalone is undocumented for Android.
Approach: extract jpegli sources + deps, create minimal CMakeLists.txt.
Risk: may require patching libjxl CMake or vendoring specific commits.

## References

- mozjpeg: https://github.com/mozilla/mozjpeg
- jpegli (in libjxl): https://github.com/libjxl/libjxl/tree/main/lib/jpegli
- MediaStore guide: https://developer.android.com/training/data-storage/shared/media
