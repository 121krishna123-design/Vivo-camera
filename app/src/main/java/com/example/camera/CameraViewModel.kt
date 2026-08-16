package com.example.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CameraUiState(
    val hasPermission: Boolean = false,
    val isReady: Boolean = false,
    val isCapturing: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    val isGridEnabled: Boolean = false,
    val hardwareList: List<CameraHardwareInfo> = emptyList(),
    val selectedHardware: CameraHardwareInfo? = null,
    val currentCameraId: String = "0",
    val lastCapturedUri: Uri? = null,
    val lastCapturedBitmap: Bitmap? = null,
    val statusMessage: String? = null,
    val showHardwareDialog: Boolean = false,
    val focusPoint: Pair<Float, Float>? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = Camera2Engine(application.applicationContext, viewModelScope)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    private val _isGridEnabled = MutableStateFlow(false)
    val isGridEnabled = _isGridEnabled.asStateFlow()

    private val _showHardwareDialog = MutableStateFlow(false)
    val showHardwareDialog = _showHardwareDialog.asStateFlow()

    private val _focusPoint = MutableStateFlow<Pair<Float, Float>?>(null)
    val focusPoint = _focusPoint.asStateFlow()

    val uiState: StateFlow<CameraUiState> = combine(
        _hasPermission,
        engine.isCapturing,
        engine.flashMode,
        engine.aspectRatio,
        _isGridEnabled,
        engine.hardwareInfoList,
        engine.selectedHardwareInfo,
        engine.currentCameraId,
        engine.lastCapturedUri,
        engine.lastCapturedBitmap,
        engine.statusMessage,
        _showHardwareDialog,
        _focusPoint
    ) { params: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        CameraUiState(
            hasPermission = params[0] as Boolean,
            isReady = params[6] != null,
            isCapturing = params[1] as Boolean,
            flashMode = params[2] as FlashMode,
            aspectRatio = params[3] as CameraAspectRatio,
            isGridEnabled = params[4] as Boolean,
            hardwareList = params[5] as List<CameraHardwareInfo>,
            selectedHardware = params[6] as CameraHardwareInfo?,
            currentCameraId = params[7] as String,
            lastCapturedUri = params[8] as Uri?,
            lastCapturedBitmap = params[9] as Bitmap?,
            statusMessage = params[10] as String?,
            showHardwareDialog = params[11] as Boolean,
            focusPoint = params[12] as Pair<Float, Float>?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CameraUiState()
    )

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            engine.scanHardware()
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (_hasPermission.value) {
            engine.setSurfaceTexture(surfaceTexture, width, height)
        }
    }

    fun onSurfaceTextureDestroyed() {
        engine.setSurfaceTexture(null, 0, 0)
    }

    fun takePicture() {
        engine.takePicture()
    }

    fun switchCamera() {
        engine.switchCamera()
    }

    fun selectCamera(id: String) {
        engine.selectCamera(id)
    }

    fun toggleFlash() {
        val nextMode = when (engine.flashMode.value) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        engine.setFlashMode(nextMode)
    }

    fun toggleAspectRatio() {
        val nextRatio = when (engine.aspectRatio.value) {
            CameraAspectRatio.RATIO_4_3 -> CameraAspectRatio.RATIO_16_9
            CameraAspectRatio.RATIO_16_9 -> CameraAspectRatio.RATIO_1_1
            CameraAspectRatio.RATIO_1_1 -> CameraAspectRatio.RATIO_4_3
        }
        engine.setAspectRatio(nextRatio)
    }

    fun toggleGrid() {
        _isGridEnabled.value = !_isGridEnabled.value
    }

    fun openHardwareDialog() {
        _showHardwareDialog.value = true
    }

    fun closeHardwareDialog() {
        _showHardwareDialog.value = false
    }

    fun onFocusTouch(xNorm: Float, yNorm: Float) {
        _focusPoint.value = Pair(xNorm, yNorm)
        engine.triggerFocusAt(xNorm, yNorm)
        // Reset focus ring after delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            if (_focusPoint.value?.first == xNorm && _focusPoint.value?.second == yNorm) {
                _focusPoint.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.closeCamera()
    }
}
