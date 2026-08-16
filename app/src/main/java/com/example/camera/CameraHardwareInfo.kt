package com.example.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size

enum class LensFacingType {
    BACK, FRONT, EXTERNAL, UNKNOWN
}

enum class FlashMode {
    OFF, AUTO, ON, TORCH
}

enum class CameraAspectRatio(val ratio: Float, val label: String) {
    RATIO_4_3(4f / 3f, "4:3"),
    RATIO_16_9(16f / 9f, "16:9"),
    RATIO_1_1(1f / 1f, "1:1")
}

data class CameraHardwareInfo(
    val cameraId: String,
    val facing: LensFacingType,
    val hardwareLevel: String,
    val sensorMegaPixels: Double,
    val activeArraySize: Rect?,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val exposureCompensationRange: Range<Int>?,
    val exposureCompensationStep: Float,
    val hasFlashUnit: Boolean,
    val supportedPreviewSizes: List<Size>,
    val supportedJpegSizes: List<Size>,
    val supportsRaw: Boolean,
    val supportsOis: Boolean,
    val supportsEis: Boolean,
    val supportedAfModes: List<Int>,
    val sensorOrientation: Int
) {
    companion object {
        fun fromCharacteristics(cameraId: String, characteristics: CameraCharacteristics): CameraHardwareInfo {
            val facingInt = characteristics.get(CameraCharacteristics.LENS_FACING)
            val facing = when (facingInt) {
                CameraMetadata.LENS_FACING_BACK -> LensFacingType.BACK
                CameraMetadata.LENS_FACING_FRONT -> LensFacingType.FRONT
                CameraMetadata.LENS_FACING_EXTERNAL -> LensFacingType.EXTERNAL
                else -> LensFacingType.UNKNOWN
            }

            val hwLevelInt = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val hwLevel = when (hwLevelInt) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3 (Flagship/Pro)"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }

            val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val mp = if (activeArray != null) {
                (activeArray.width().toDouble() * activeArray.height().toDouble()) / 1_000_000.0
            } else 0.0

            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val expTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val aeCompRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val aeCompStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f

            val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()

            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val rawSupported = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)

            val oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val hasOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

            val eisModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            val hasEis = eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList()
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            return CameraHardwareInfo(
                cameraId = cameraId,
                facing = facing,
                hardwareLevel = hwLevel,
                sensorMegaPixels = mp,
                activeArraySize = activeArray,
                isoRange = isoRange,
                exposureTimeRangeNs = expTimeRange,
                exposureCompensationRange = aeCompRange,
                exposureCompensationStep = aeCompStep,
                hasFlashUnit = flashAvailable,
                supportedPreviewSizes = previewSizes,
                supportedJpegSizes = jpegSizes,
                supportsRaw = rawSupported,
                supportsOis = hasOis,
                supportsEis = hasEis,
                supportedAfModes = afModes,
                sensorOrientation = sensorOrientation
            )
        }
    }
}
