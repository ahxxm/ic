package io.ahxxm.ic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ahxxm.ic.domain.ImageCompressionPreview

/**
 * One Review visit over a compressed batch. Selections, the open comparison, and the compressed
 * temporary files live here for the whole visit; opening or closing comparison never touches them.
 *
 * [discard] is the only operation that deletes the temp files. [confirm] hands them to Progress,
 * which then consumes and deletes them itself.
 */
class ReviewSession(previews: List<ImageCompressionPreview>) {

    val previews: List<ImageCompressionPreview> = previews.toList()

    var selections by mutableStateOf(defaultSelections())
        private set

    var comparisonIndex by mutableStateOf<Int?>(null)
        private set

    val isComparing: Boolean get() = comparisonIndex != null

    private var confirmed = false

    fun setSelected(index: Int, selected: Boolean) {
        selections = selections.toMutableList().apply { this[index] = selected }
    }

    fun selectAll() {
        selections = List(previews.size) { true }
    }

    fun deselectAll() {
        selections = List(previews.size) { false }
    }

    fun smartSelect() {
        selections = defaultSelections()
    }

    fun openComparison(index: Int) {
        comparisonIndex = index
    }

    fun closeComparison() {
        comparisonIndex = null
    }

    fun confirm(): List<ImageCompressionPreview> {
        confirmed = true
        return previews.filterIndexed { index, _ -> selections[index] }
    }

    fun discard() {
        if (confirmed) return
        previews.forEach { it.tempFile?.delete() }
    }

    private fun defaultSelections(): List<Boolean> =
        previews.map {
            !ImageCompressionPreview.shouldAutoDeselect(it.savingsPercent, it.savingsBytes)
        }
}
