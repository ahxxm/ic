# Implementation Plan

## Phase 0: Project Scaffold

**Concrete steps:**
- [x] Create Android project with Gradle wrapper
- [x] Configure `build.gradle.kts` (compileSdk=35, minSdk=30, Compose enabled)
- [x] Create F-Droid metadata structure
- [x] Basic MainActivity + single NavHost with placeholder screens

**Verification:** `./gradlew assembleDebug` (requires x86-64 build environment)

---

## Phase 1: Storage & File Handling

**Manifest:**
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- For Android 11-12 fallback -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

**Concrete steps:**
- [x] Request permission based on API level:
  ```kotlin
  val permission = if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_IMAGES
  } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
  }
  ```
- [x] Query folders via MediaStore bucket grouping:
  ```kotlin
  val projection = arrayOf(
      MediaStore.Images.Media.BUCKET_ID,
      MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Images.Media.SIZE,
      MediaStore.Images.Media._ID
  )
  contentResolver.query(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      projection,
      null, null,
      "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC"
  )
  ```
- [x] Aggregate into folder summaries:
  ```kotlin
  data class FolderSummary(
      val bucketId: Long,
      val name: String,
      val imageCount: Int,
      val totalSizeBytes: Long
  )
  ```
- [x] Build folder browser UI showing "DCIM/Camera - 2,847 images, 4.2GB"
- [x] On folder select, query images in bucket:
  ```kotlin
  data class ImageItem(
      val uri: Uri,  // content://media/external/images/media/{id}
      val name: String,
      val sizeBytes: Long,
      val mimeType: String  // image/jpeg, image/png
  )
  ```
- [x] Filter by MIME type: `image/jpeg` processable, `image/png` processable if convert enabled (ImageItem.isProcessable)
- [ ] Display in UI: "47 JPEG, 12 PNG | 8 skipped" (deferred to Phase 4 Preview screen)

**Verification:** Grant permission, browse folders, see image counts, select folder for compression

---

## Phase 2: Options Screen

**Concrete steps:**
- [x] Quality slider: `Slider(value = quality, valueRange = 70f..95f, steps = 24)`
- [x] EXIF preserve: `Checkbox(checked = preserveExif)` — default ON
- [x] PNG convert: `Checkbox(checked = convertPng)` — default OFF
- [x] Encoder toggle: disabled, shows "mozjpeg" (jpegli added Phase 7)
- [x] State holder:
  ```kotlin
  data class CompressionOptions(
      val quality: Int = 80,
      val preserveExif: Boolean = true,
      val convertPng: Boolean = false,
      val encoder: Encoder = Encoder.MOZJPEG
  )
  ```

**Verification:** Options persist across configuration changes (via `rememberSaveable`)

---

## Phase 3: Compression - Stub (Bitmap.compress)

Using Android's built-in encoder to validate full flow before NDK.

**Concrete steps:**
- [ ] Compression function:
  ```kotlin
  suspend fun compressImage(
      image: ImageItem,  // contains Uri from MediaStore
      options: CompressionOptions,
      context: Context
  ): CompressionResult {
      val inputStream = context.contentResolver.openInputStream(image.uri)
      val bitmap = BitmapFactory.decodeStream(inputStream)

      val tempFile = File(context.cacheDir, "temp_${image.name}")
      FileOutputStream(tempFile).use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, out)
      }

      return CompressionResult(
          originalSize = image.sizeBytes,
          compressedSize = tempFile.length(),
          tempFile = tempFile,
          success = true
      )
  }
  ```
- [ ] EXIF copy (when preserveExif=true):
  ```kotlin
  // Use androidx.exifinterface
  implementation("androidx.exifinterface:exifinterface:1.3.7")

  val originalExif = ExifInterface(inputStream)
  val newExif = ExifInterface(tempFile)
  // Copy relevant tags
  ```
- [ ] Wire random sample preview on options confirm

**Verification:** Select options → see one image compressed → shows original vs new size

---

## Phase 4: Savings Preview Screen

**Concrete steps:**
- [ ] Batch compress all selected images to cache dir (suspend, show progress)
- [ ] Results list:
  ```kotlin
  data class ImageCompressionPreview(
      val image: ImageItem,  // contains Uri from MediaStore
      val originalSize: Long,
      val compressedSize: Long,
      val savingsBytes: Long,
      val savingsPercent: Float,
      val selected: Boolean  // mutable
  )
  ```
- [ ] LazyColumn sorted by `savingsBytes` DESC
- [ ] Auto-deselect logic:
  ```kotlin
  val autoDeselect = savingsPercent < 0.10f && savingsBytes < 50_000
  ```
- [ ] Total savings display (sum of selected items)
- [ ] Tap row → navigate to before/after comparison screen
- [ ] Comparison screen: two `AsyncImage` (Coil) side by side or swipeable

**Dependency:**
```kotlin
implementation("io.coil-kt:coil-compose:2.5.0")
```

**Verification:** See list of all images with savings, auto-deselected items grayed, can toggle, total updates

---

## Phase 5: Commit Flow

**Manifest:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".CompressionService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

**Concrete steps:**
- [ ] Request POST_NOTIFICATIONS permission (Android 13+)
- [ ] Before starting service, request write access:
  ```kotlin
  val urisToModify = selectedImages.map { it.image.uri }
  val writeRequest = MediaStore.createWriteRequest(contentResolver, urisToModify)
  startIntentSenderForResult(writeRequest.intentSender, REQUEST_WRITE_ACCESS, ...)
  ```
- [ ] On user approval, start CompressionService:
  ```kotlin
  override fun onStartCommand(...): Int {
      startForeground(NOTIF_ID, buildNotification(0, total))
      scope.launch {
          selectedImages.forEachIndexed { i, img ->
              // Write access already granted via createWriteRequest
              overwriteImage(img.image.uri, tempFiles[i])
              updateNotification(i + 1, total)
          }
          stopSelf()
      }
      return START_NOT_STICKY
  }
  ```
- [ ] Overwrite via ContentResolver:
  ```kotlin
  fun overwriteImage(uri: Uri, tempFile: File) {
      contentResolver.openOutputStream(uri, "wt")?.use { out ->
          tempFile.inputStream().use { it.copyTo(out) }
      }
  }
  ```
- [ ] Delete temp files after each successful write
- [ ] Done screen: "Saved X.X MB across N images"

**Verification:** Start compression, see progress notification, files overwritten, temp cleaned

---

## Phase 6: NDK - Mozjpeg

**Deferred until Phase 0-5 complete.**

**Concrete steps:**
- [ ] Add mozjpeg as git submodule: `git submodule add https://github.com/mozilla/mozjpeg.git app/src/main/cpp/mozjpeg`
- [ ] `app/src/main/cpp/CMakeLists.txt`:
  ```cmake
  cmake_minimum_required(VERSION 3.22)
  project(imagecompressor)

  set(MOZJPEG_DIR ${CMAKE_SOURCE_DIR}/mozjpeg)
  add_subdirectory(${MOZJPEG_DIR})

  add_library(compressor SHARED compressor.cpp)
  target_link_libraries(compressor jpeg-static)
  ```
- [ ] JNI interface in `compressor.cpp`:
  ```cpp
  extern "C" JNIEXPORT jbyteArray JNICALL
  Java_com_example_imagecompressor_NativeCompressor_compress(
      JNIEnv *env, jobject,
      jbyteArray pixels, jint width, jint height, jint quality
  ) { ... }
  ```
- [ ] Kotlin binding:
  ```kotlin
  object NativeCompressor {
      init { System.loadLibrary("compressor") }
      external fun compress(pixels: ByteArray, width: Int, height: Int, quality: Int): ByteArray
  }
  ```
- [ ] Replace Bitmap.compress call with native call
- [ ] Test F-Droid build: `fdroid build -l com.example.imagecompressor`

**Verification:** APK uses mozjpeg, F-Droid build server can compile from source

---

## Phase 7: NDK - Jpegli

**Deferred until Phase 6 stable.**

- [ ] Extract jpegli sources from libjxl
- [ ] Standalone CMakeLists.txt (may require patching)
- [ ] JNI wrapper matching mozjpeg interface
- [ ] Encoder toggle becomes functional
- [ ] Compare output quality/size vs mozjpeg

---

## Phase 8: Polish

- [ ] Error handling: permission denied, disk full, corrupt image
- [ ] Batch interruption: save progress, resume capability
- [ ] Multi-folder management
- [ ] Settings persistence (DataStore)
- [ ] Proguard/R8 rules for native libs

---

## Current Focus

**Phase 3** - Compression stub (Bitmap.compress). Phase 1-2 complete.

## Risk Register

| Risk | Mitigation |
|------|------------|
| MediaStore query performance on large libraries | Process cursor in chunks, show loading indicator |
| Mozjpeg NDK build complexity | Defer until flow validated with Bitmap.compress |
| Jpegli standalone build undocumented | May need to vendor specific libjxl commit, patch CMake |
| EXIF copy edge cases | Use androidx.exifinterface, test with various camera apps |

## Sources

- [Jetpack Compose Setup](https://developer.android.com/develop/ui/compose/setup)
- [MediaStore Access Guide](https://developer.android.com/training/data-storage/shared/media)
- [F-Droid Metadata Structure](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- [Foreground Service Requirements](https://developer.android.com/develop/background-work/services/fgs/launch)
- [Mozjpeg Build Instructions](https://github.com/mozilla/mozjpeg/blob/master/BUILDING.md)
- [Android NDK CMake Guide](https://developer.android.com/ndk/guides/cmake)
