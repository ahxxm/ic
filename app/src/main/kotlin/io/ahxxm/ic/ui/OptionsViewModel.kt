package io.ahxxm.ic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.CompressionResult
import io.ahxxm.ic.domain.Encoder
import io.ahxxm.ic.domain.ImageCompressor
import io.ahxxm.ic.domain.ImageItem
import io.ahxxm.ic.domain.ImageRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class QualityLevel(val label: String) {
    OK("ok"),
    GOOD("good"),
    GREAT("great");

    fun qualityFor(encoder: Encoder): Int = when (this) {
        OK -> if (encoder == Encoder.MOZJPEG) 75 else 65
        GOOD -> if (encoder == Encoder.MOZJPEG) 85 else 75
        GREAT -> if (encoder == Encoder.MOZJPEG) 90 else 82
    }
}

sealed class SampleState {
    data object Loading : SampleState()
    data class Ready(val image: ImageItem, val result: CompressionResult) : SampleState()
    data class Error(val message: String) : SampleState()
}

@OptIn(FlowPreview::class)
class OptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val compressor = ImageCompressor(application)
    private val repository = ImageRepository(application.contentResolver)

    private var currentTempFile: File? = null
    private var sampleImage: ImageItem? = null

    val qualityLevel = MutableStateFlow(QualityLevel.GOOD)
    val preserveExif = MutableStateFlow(true)
    val convertPng = MutableStateFlow(true)
    val encoder = MutableStateFlow(Encoder.MOZJPEG)

    private val _sampleState = MutableStateFlow<SampleState>(SampleState.Loading)
    val sampleState: StateFlow<SampleState> = _sampleState

    private val _navigateBack = Channel<Unit>(Channel.BUFFERED)
    val navigateBack = _navigateBack.receiveAsFlow()

    private val compressionTrigger = combine(
        qualityLevel, encoder, preserveExif
    ) { q, e, p -> Triple(q, e, p) }
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            compressionTrigger.collect { params ->
                params ?: return@collect
                val sample = sampleImage ?: return@collect
                compress(sample, params.first, params.second, params.third)
            }
        }
    }

    fun loadSampleImage(bucketId: Long) {
        viewModelScope.launch {
            val images = repository.getImagesInFolder(bucketId)
                .filter { it.isJpeg || it.isPng }

            val current = sampleImage
            if (current != null && images.any { it.uri == current.uri }) {
                return@launch
            }

            val sample = images.randomOrNull()
            if (sample == null) {
                _navigateBack.send(Unit)
                return@launch
            }

            sampleImage = sample
            compress(sample, qualityLevel.value, encoder.value, preserveExif.value)
        }
    }

    fun setConvertPng(bucketId: Long, enabled: Boolean) {
        if (convertPng.value == enabled) return
        convertPng.value = enabled
        sampleImage = null
        viewModelScope.launch {
            val images = repository.getImagesInFolder(bucketId)
                .filter { if (enabled) it.isJpeg || it.isPng else it.isJpeg }

            val sample = images.randomOrNull()
            if (sample == null) {
                _navigateBack.send(Unit)
                return@launch
            }
            sampleImage = sample
            compress(sample, qualityLevel.value, encoder.value, preserveExif.value)
        }
    }

    fun buildOptions(): CompressionOptions = CompressionOptions(
        quality = qualityLevel.value.qualityFor(encoder.value),
        preserveExif = preserveExif.value,
        convertPng = convertPng.value,
        encoder = encoder.value
    )

    private suspend fun compress(
        image: ImageItem,
        quality: QualityLevel,
        enc: Encoder,
        keepExif: Boolean
    ) {
        _sampleState.value = SampleState.Loading
        currentTempFile?.delete()

        val options = CompressionOptions(
            quality = quality.qualityFor(enc),
            preserveExif = keepExif,
            convertPng = convertPng.value,
            encoder = enc
        )
        val result = compressor.compressImage(image, options)
        currentTempFile = result.tempFile

        _sampleState.value = if (result.success) {
            SampleState.Ready(image, result)
        } else {
            SampleState.Error(result.error ?: "Compression failed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentTempFile?.delete()
    }
}
