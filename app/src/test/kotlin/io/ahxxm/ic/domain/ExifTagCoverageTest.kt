package io.ahxxm.ic.domain

import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Drives the `exif-tag-diff` CI job, which prints app/build/exif-tag-diff.txt.
// Report only: androidx gaining tags we don't copy is information, not breakage.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExifTagCoverageTest {

    @Test
    fun `report diff between ExifInterface tags and copied list`() {
        val library = ExifInterface::class.java.fields
            .filter { it.type == String::class.java && it.name.startsWith("TAG_") }
            .associate { it.get(null) as String to it.name }

        val copied = ImageCompressor.EXIF_TAGS.toSet()

        val dropped = (library.keys - copied)
            .mapNotNull { library[it]?.let { name -> name to it } }
            .sortedBy { (name, _) -> name }
        val unknown = (copied - library.keys).sorted()

        val report = buildString {
            appendLine("ExifInterface defines ${library.size} tags, ${copied.size} are copied")
            appendLine()
            appendLine("not copied (dropped on compression): ${dropped.size}")
            dropped.forEach { (name, value) -> appendLine("  $name ($value)") }
            appendLine()
            appendLine("copied but absent from ExifInterface: ${unknown.size}")
            unknown.forEach { appendLine("  $it") }
        }

        println(report)
        File("build/exif-tag-diff.txt").writeText(report)
    }
}
