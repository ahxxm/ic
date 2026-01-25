# Image Compressor

A minimalist and opinionated Android app for offline/on-device JPEG/PNG compression.

## Features

- Browse and select image folders
- 3 built-in quality levels for MozJPEG and Jpegli
- "Smart" filter to skip well-compressed images
- Preview estimated savings before applying compression

Options:
- EXIF metadata preservation
- PNG to JPEG conversion

Encoders:
- mozjpeg: Mozilla's optimized JPEG encoder
- jpegli: Google's high-quality JPEG encoder from libjxl

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## License

MIT
