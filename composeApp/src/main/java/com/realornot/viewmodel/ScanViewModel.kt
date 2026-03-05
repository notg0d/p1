package com.realornot.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realornot.data.AppDatabase
import com.realornot.inference.AnalysisResult
import com.realornot.inference.DeepfakeAnalyzer
import com.realornot.model.MediaType
import com.realornot.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val currentStep: Int = 0,
    val result: AnalysisResult? = null,
    val selectedUri: Uri? = null,
    val mediaType: MediaType = MediaType.IMAGE,
    val fileName: String = "",
    val error: String? = null,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.scanResultDao()
    private val analyzer = DeepfakeAnalyzer(application)

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    val scanHistory: Flow<List<ScanResult>> = dao.getAll()
    val scanCount: Flow<Int> = dao.getCount()

    fun startScan(uri: Uri, mimeType: String?, fileName: String) {
        val mediaType = MediaType.fromMimeType(mimeType)

        _state.update {
            it.copy(
                isScanning = true,
                progress = 0f,
                statusMessage = "File uploaded successfully",
                currentStep = 0,
                result = null,
                selectedUri = uri,
                mediaType = mediaType,
                fileName = fileName,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(currentStep = 1, statusMessage = "Preprocessing...") }
                }
                kotlinx.coroutines.delay(400)

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(currentStep = 2, statusMessage = "Running AI analysis...") }
                }

                val result = analyzer.analyze(uri, mediaType) { progress, message ->
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(progress = progress, statusMessage = message)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            currentStep = 3,
                            statusMessage = "Analysis complete!",
                            progress = 1f,
                            result = result,
                            isScanning = false,
                        )
                    }
                }

                dao.insert(
                    ScanResult(
                        fileName = fileName,
                        mediaType = mediaType.name,
                        verdict = result.verdict,
                        confidence = result.confidence,
                        modelUsed = result.modelUsed,
                        processingTimeMs = result.processingTimeMs,
                        videoVerdict = result.videoVerdict,
                        videoConfidence = result.videoConfidence,
                        audioVerdict = result.audioVerdict,
                        audioConfidence = result.audioConfidence,
                        reasoning = result.reasoning,
                    )
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            error = e.message ?: "Analysis failed",
                        )
                    }
                }
            }
        }
    }

    /** Show a past scan result from history */
    fun showHistoryResult(scanResult: ScanResult) {
        _state.update {
            ScanState(
                isScanning = false,
                progress = 1f,
                statusMessage = "Analysis complete!",
                currentStep = 3,
                mediaType = try { MediaType.valueOf(scanResult.mediaType) } catch (_: Exception) { MediaType.IMAGE },
                fileName = scanResult.fileName,
                result = AnalysisResult(
                    verdict = scanResult.verdict,
                    confidence = scanResult.confidence,
                    modelUsed = scanResult.modelUsed,
                    processingTimeMs = scanResult.processingTimeMs,
                    videoVerdict = scanResult.videoVerdict,
                    videoConfidence = scanResult.videoConfidence,
                    audioVerdict = scanResult.audioVerdict,
                    audioConfidence = scanResult.audioConfidence,
                    reasoning = scanResult.reasoning,
                ),
            )
        }
    }

    fun resetState() {
        _state.update { ScanState() }
    }

    override fun onCleared() {
        super.onCleared()
        analyzer.close()
    }
}
