package io.ahxxm.ic

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.CompressionResult
import io.ahxxm.ic.domain.Encoder
import io.ahxxm.ic.domain.ImageCompressor
import io.ahxxm.ic.domain.ImageItem
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread
import kotlin.math.roundToInt

// Black-box memory profile of ImageCompressor over real fixtures: while one
// image goes through compressImage, peak RSS is sampled from /proc/self/status.
// A markdown report is written to the app's filesDir; CI pulls it after
// connectedDebugAndroidTest. jpegli runs with its current progressive=true
// behavior; mozjpeg is progressive upstream with no switch.
@RunWith(AndroidJUnit4::class)
class MemoryProfileTest {

    @Test
    fun profileEncoders() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val am = appContext.getSystemService(ActivityManager::class.java)
        val compressor = ImageCompressor(appContext)
        val rows = mutableListOf<Row>()

        val fixtures = instrumentation.context.assets.list(FIXTURES_DIR)!!
            .filter { it.endsWith(".jpg") }
            .sorted()

        for (fixture in fixtures) {
            val sourceFile = copyAsset(appContext.cacheDir, FIXTURES_DIR, fixture)
            val (w, h) = decodeBounds(sourceFile)
            val halved = ImageCompressor.shouldHalve(w, h, halveOversized = true)
            val decodedW = if (halved) w / 2 else w
            val decodedH = if (halved) h / 2 else h

            // Quality mirrors QualityLevel.GOOD in OptionsViewModel
            val cells = listOf(Encoder.MOZJPEG to 85, Encoder.JPEGLI to 75)
            for ((encoder, quality) in cells) {
                val uri = insertToMediaStore(appContext, sourceFile, fixture)
                val item = ImageItem(uri, fixture, sourceFile.length(), "image/jpeg")
                val options = CompressionOptions(quality = quality, encoder = encoder)

                val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0
                val nativeBefore = Debug.getNativeHeapAllocatedSize()
                val sampler = RssSampler().start()
                val t0 = System.nanoTime()

                val result = try {
                    compressor.compressImage(item, options)
                } catch (t: Throwable) {
                    CompressionResult(sourceFile.length(), 0, null, false, t.toString())
                }

                val ms = (System.nanoTime() - t0) / 1_000_000
                val rssPeakKb = sampler.stop()
                val nativeDeltaKb = (Debug.getNativeHeapAllocatedSize() - nativeBefore) / 1024
                val gcDelta = (Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0) - gcBefore

                rows.add(
                    Row(
                        fixture = fixture.substringBefore("-unsplash"),
                        sourceMp = mp(w.toLong(), h.toLong()),
                        decodedMp = mp(decodedW.toLong(), decodedH.toLong()),
                        encoder = encoder,
                        status = if (result.success) "ok" else "fail: ${result.error}",
                        outKb = result.compressedSize / 1024,
                        rssPeakKb = rssPeakKb,
                        nativeDeltaKb = nativeDeltaKb,
                        gcDelta = gcDelta,
                        ms = ms
                    )
                )

                result.tempFile?.delete()
                appContext.contentResolver.delete(uri, null, null)
                System.gc()
                Thread.sleep(200)
            }

            sourceFile.delete()
        }

        val report = buildReport(am, rows)
        File(appContext.filesDir, REPORT_NAME).writeText(report)
        println(report)
    }

    private fun mp(w: Long, h: Long): Double =
        (w * h / 100.0).roundToInt() / 10_000.0

    private fun copyAsset(cacheDir: File, assetDir: String, name: String): File {
        val out = File(cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("$assetDir/$name").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        return out
    }

    private fun decodeBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth to options.outHeight
    }

    // compressImage reads through MediaStore (setRequireOriginal), so each
    // fixture is inserted as an app-owned image and deleted afterwards.
    private fun insertToMediaStore(context: Context, file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/icprofile")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)!!
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private class RssSampler {
        private var running = true
        private var peak = 0L
        private lateinit var worker: Thread

        fun start(): RssSampler {
            worker = thread(name = "rss-sampler") {
                while (running) {
                    currentRssKb()?.let { if (it > peak) peak = it }
                    Thread.sleep(20)
                }
            }
            return this
        }

        fun stop(): Long {
            running = false
            worker.join(500)
            return peak
        }

        private fun currentRssKb(): Long? = try {
            File("/proc/self/status").bufferedReader().useLines { lines ->
                lines.find { it.startsWith("VmRSS:") }
                    ?.split(Regex("\\s+"))?.getOrNull(1)?.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class Row(
        val fixture: String,
        val sourceMp: Double,
        val decodedMp: Double,
        val encoder: Encoder,
        val status: String,
        val outKb: Long,
        val rssPeakKb: Long,
        val nativeDeltaKb: Long,
        val gcDelta: Long,
        val ms: Long
    )

    private fun buildReport(am: ActivityManager?, rows: List<Row>): String = buildString {
        appendLine("# Memory profile report")
        appendLine()
        appendLine(
            "device: API ${Build.VERSION.SDK_INT}, ${Build.MODEL}, " +
                "memoryClass ${am?.memoryClass}MB, largeMemoryClass ${am?.largeMemoryClass}MB, " +
                "isLowRamDevice ${am?.isLowRamDevice}"
        )
        appendLine()
        appendLine("| fixture | source MP | decoded MP | encoder | status | out KB | RSS peak MB | native Δ MB | GC Δ | ms |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        rows.forEach { r ->
            appendLine(
                "| ${r.fixture} | ${r.sourceMp} | ${r.decodedMp} | ${r.encoder} | ${r.status} | " +
                    "${r.outKb} | ${r.rssPeakKb / 1024.0} | ${r.nativeDeltaKb / 1024.0} | ${r.gcDelta} | ${r.ms} |"
            )
        }
    }

    companion object {
        private const val FIXTURES_DIR = "fixtures"
        private const val REPORT_NAME = "memory-profile.md"
    }
}
