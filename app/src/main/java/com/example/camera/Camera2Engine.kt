package com.example.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private const val TAG = "Camera2Engine"

class Camera2Engine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null

    private var previewSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null

    private val cameraOpenCloseLock = Semaphore(1)

    private val _currentCameraId = MutableStateFlow<String>("0")
    val currentCameraId = _currentCameraId.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    val flashMode = _flashMode.asStateFlow()

    private val _aspectRatio = MutableStateFlow(CameraAspectRatio.RATIO_4_3)
    val aspectRatio = _aspectRatio.asStateFlow()

    private val _hardwareInfoList = MutableStateFlow<List<CameraHardwareInfo>>(emptyList())
    val hardwareInfoList = _hardwareInfoList.asStateFlow()

    private val _selectedHardwareInfo = MutableStateFlow<CameraHardwareInfo?>(null)
    val selectedHardwareInfo = _selectedHardwareInfo.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val _lastCapturedUri = MutableStateFlow<Uri?>(null)
    val lastCapturedUri = _lastCapturedUri.asStateFlow()

    private val _lastCapturedBitmap = MutableStateFlow<Bitmap?>(null)
    val lastCapturedBitmap = _lastCapturedBitmap.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private var previewSize: Size = Size(1920, 1080)
    private var jpegSize: Size = Size(4000, 3000)

    init {
        try {
            scanHardware()
        } catch (e: Throwable) {
            Log.e(TAG, "Error in init scanHardware", e)
        }
    }

    fun scanHardware() {
        try {
            val list = mutableListOf<CameraHardwareInfo>()
            val ids = try {
                cameraManager.cameraIdList
            } catch (e: Throwable) {
                emptyArray<String>()
            }

            for (id in ids) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    list.add(CameraHardwareInfo.fromCharacteristics(id, chars))
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not get characteristics for cam $id", e)
                }
            }

            if (list.isEmpty()) {
                // Fallback default info for emulator / virtual preview
                list.add(
                    CameraHardwareInfo(
                        cameraId = "0",
                        facing = LensFacingType.BACK,
                        hardwareLevel = "LEVEL_3 (ProCam Engine)",
                        sensorMegaPixels = 50.0,
                        activeArraySize = Rect(0, 0, 8192, 6144),
                        isoRange = Range(50, 6400),
                        exposureTimeRangeNs = Range(10_000L, 30_000_000_000L),
                        exposureCompensationRange = Range(-12, 12),
                        exposureCompensationStep = 0.33f,
                        hasFlashUnit = true,
                        supportedPreviewSizes = listOf(Size(1920, 1080), Size(1280, 720)),
                        supportedJpegSizes = listOf(Size(4000, 3000), Size(1920, 1080)),
                        supportsRaw = true,
                        supportsOis = true,
                        supportsEis = true,
                        supportedAfModes = listOf(1, 4),
                        sensorOrientation = 90
                    )
                )
            }

            _hardwareInfoList.value = list
            if (_selectedHardwareInfo.value == null) {
                val backCam = list.firstOrNull { it.facing == LensFacingType.BACK } ?: list.first()
                _currentCameraId.value = backCam.cameraId
                _selectedHardwareInfo.value = backCam
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error scanning camera hardware", e)
        }
    }

    private fun startBackgroundThread() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("Camera2Background").apply {
                start()
                cameraHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun setSurfaceTexture(texture: SurfaceTexture?, width: Int, height: Int) {
        this.surfaceTexture = texture
        if (texture != null) {
            startBackgroundThread()
            openCamera(_currentCameraId.value)
        } else {
            closeCamera()
            stopBackgroundThread()
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String) {
        startBackgroundThread()
        val info = _hardwareInfoList.value.firstOrNull { it.cameraId == cameraId }
        _currentCameraId.value = cameraId
        _selectedHardwareInfo.value = info

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Time out waiting to lock camera opening.")
                return
            }

            val characteristics = try {
                cameraManager.getCameraCharacteristics(cameraId)
            } catch (e: Throwable) {
                null
            }

            if (characteristics != null) {
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                if (map != null) {
                    val targetRatio = _aspectRatio.value.ratio
                    // Select optimal JPEG size matching ratio
                    val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
                    if (jpegSizes != null && jpegSizes.isNotEmpty()) {
                        jpegSize = chooseOptimalJpegSize(jpegSizes, targetRatio)
                    }

                    // Select optimal Preview size matching ratio
                    val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
                    if (previewSizes != null && previewSizes.isNotEmpty()) {
                        previewSize = chooseOptimalPreviewSize(previewSizes, targetRatio)
                    }

                    // Setup ImageReader for still capture
                    imageReader?.close()
                    imageReader = ImageReader.newInstance(
                        jpegSize.width,
                        jpegSize.height,
                        ImageFormat.JPEG,
                        2
                    ).apply {
                        setOnImageAvailableListener(imageAvailableListener, cameraHandler)
                    }
                }

                cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
            } else {
                cameraOpenCloseLock.release()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot access the camera: $cameraId", e)
            try {
                cameraOpenCloseLock.release()
            } catch (ignored: Exception) {}
        }
    }

    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            previewSurface?.release()
            previewSurface = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera device error: $error")
            _statusMessage.value = "Camera error: $error"
        }
    }

    private fun createCameraPreviewSession() {
        val device = cameraDevice ?: return
        val texture = surfaceTexture ?: return
        val reader = imageReader ?: return

        try {
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface?.release()
            val surface = Surface(texture)
            previewSurface = surface

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                applyFlash(this, _flashMode.value)
            }

            val surfaces = listOf(surface, reader.surface)
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            previewRequestBuilder?.let { builder ->
                                session.setRepeatingRequest(
                                    builder.build(),
                                    null,
                                    cameraHandler
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start camera preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Configuration failed for camera session")
                        _statusMessage.value = "Preview configuration failed"
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating camera preview session", e)
        }
    }

    fun switchCamera() {
        val list = _hardwareInfoList.value
        if (list.size <= 1) return

        val currentFacing = _selectedHardwareInfo.value?.facing ?: LensFacingType.BACK
        val targetFacing = if (currentFacing == LensFacingType.BACK) LensFacingType.FRONT else LensFacingType.BACK

        val nextCam = list.firstOrNull { it.facing == targetFacing }
            ?: list.firstOrNull { it.cameraId != _currentCameraId.value }
            ?: list.first()

        closeCamera()
        openCamera(nextCam.cameraId)
    }

    fun selectCamera(id: String) {
        if (id == _currentCameraId.value) return
        closeCamera()
        openCamera(id)
    }

    fun setFlashMode(mode: FlashMode) {
        _flashMode.value = mode
        previewRequestBuilder?.let { builder ->
            applyFlash(builder, mode)
            try {
                captureSession?.setRepeatingRequest(builder.build(), null, cameraHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating flash mode", e)
            }
        }
    }

    fun setAspectRatio(ratio: CameraAspectRatio) {
        _aspectRatio.value = ratio
        closeCamera()
        openCamera(_currentCameraId.value)
    }

    private fun applyFlash(builder: CaptureRequest.Builder, mode: FlashMode) {
        val hasFlash = _selectedHardwareInfo.value?.hasFlashUnit ?: false
        if (!hasFlash) return

        when (mode) {
            FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.ON -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }
    }

    fun takePicture() {
        if (_isCapturing.value) return
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        _isCapturing.value = true
        _statusMessage.value = "Capturing..."

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                applyFlash(this, _flashMode.value)

                // Compute orientation
                val sensorOrientation = _selectedHardwareInfo.value?.sensorOrientation ?: 90
                val isFront = _selectedHardwareInfo.value?.facing == LensFacingType.FRONT
                val rotation = computeJpegRotation(sensorOrientation, isFront)
                set(CaptureRequest.JPEG_ORIENTATION, rotation)
                set(CaptureRequest.JPEG_QUALITY, 98.toByte())
            }

            session.stopRepeating()
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d(TAG, "Capture completed successfully")
                        // Restart preview
                        try {
                            previewRequestBuilder?.let { builder ->
                                session.setRepeatingRequest(builder.build(), null, cameraHandler)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restarting preview", e)
                        }
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture still picture", e)
            _isCapturing.value = false
            _statusMessage.value = "Capture failed: ${e.message}"
        }
    }

    fun triggerFocusAt(xNorm: Float, yNorm: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val activeArray = _selectedHardwareInfo.value?.activeArraySize ?: return

        val focusAreaSize = 200
        val centerX = (xNorm * activeArray.width()).toInt()
        val centerY = (yNorm * activeArray.height()).toInt()

        val left = (centerX - focusAreaSize / 2).coerceIn(0, activeArray.width() - focusAreaSize)
        val top = (centerY - focusAreaSize / 2).coerceIn(0, activeArray.height() - focusAreaSize)
        val right = left + focusAreaSize
        val bottom = top + focusAreaSize

        val focusRect = Rect(left, top, right, bottom)
        val meteringRectangle = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)

        try {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            session.capture(builder.build(), null, cameraHandler)

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering focus", e)
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                val (uri, bitmap) = saveImageToStorage(bytes)
                withContext(Dispatchers.Main) {
                    _lastCapturedUri.value = uri
                    _lastCapturedBitmap.value = bitmap
                    _isCapturing.value = false
                    _statusMessage.value = "Photo saved: ${uri?.lastPathSegment ?: "Gallery"}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing captured photo", e)
                withContext(Dispatchers.Main) {
                    _isCapturing.value = false
                    _statusMessage.value = "Save error: ${e.message}"
                }
            }
        }
    }

    private fun saveImageToStorage(jpegBytes: ByteArray): Pair<Uri?, Bitmap?> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PROCAM_${timeStamp}.jpg"
        var uri: Uri? = null
        var thumbBitmap: Bitmap? = null

        // Generate thumbnail safely with sub-sampling to prevent memory exhaustion & ashmem warnings
        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, boundsOptions)

            val maxDim = 256
            var sampleSize = 1
            if (boundsOptions.outHeight > maxDim || boundsOptions.outWidth > maxDim) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDim && (halfWidth / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sampleBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
            if (sampleBitmap != null) {
                val scale = maxDim.toFloat() / maxOf(sampleBitmap.width, sampleBitmap.height).coerceAtLeast(1)
                thumbBitmap = Bitmap.createScaledBitmap(
                    sampleBitmap,
                    (sampleBitmap.width * scale).toInt().coerceAtLeast(1),
                    (sampleBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating thumbnail", e)
        }

        // Save to MediaStore (Android 10+) or external directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jpegBytes)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ProCam"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                out.write(jpegBytes)
            }
            uri = Uri.fromFile(file)
        }

        return Pair(uri, thumbBitmap)
    }

    private fun computeJpegRotation(sensorOrientation: Int, isFrontCamera: Boolean): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val deviceRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        val deviceDegrees = when (deviceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        return if (isFrontCamera) {
            (sensorOrientation + deviceDegrees) % 360
        } else {
            (sensorOrientation - deviceDegrees + 360) % 360
        }
    }

    private fun chooseOptimalPreviewSize(choices: Array<Size>, targetRatio: Float): Size {
        val valid = choices.filter {
            val r = it.width.toFloat() / it.height.toFloat()
            Math.abs(r - targetRatio) < 0.1f && it.width <= 1920 && it.height <= 1080
        }
        return valid.maxByOrNull { it.width * it.height }
            ?: choices.firstOrNull { it.width <= 1920 }
            ?: choices[0]
    }

    private fun chooseOptimalJpegSize(choices: Array<Size>, targetRatio: Float): Size {
        val matching = choices.filter {
            val r = it.width.toFloat() / it.height.toFloat()
            Math.abs(r - targetRatio) < 0.08f
        }
        return matching.maxByOrNull { it.width * it.height }
            ?: choices.maxByOrNull { it.width * it.height }
            ?: choices[0]
    }
}
