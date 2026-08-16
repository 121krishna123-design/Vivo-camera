package com.example.camera

import android.Manifest
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GridLine
import com.example.ui.theme.LensRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Check permission initially
    LaunchedEffect(Unit) {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PermissionChecker.PERMISSION_GRANTED
        viewModel.onPermissionResult(hasCam)
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    // Status message snackbar
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (!uiState.hasPermission) {
            PermissionRequestView(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            CameraViewfinderContent(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    // Hardware Specs Bottom Sheet
    if (uiState.showHardwareDialog) {
        HardwareSpecsBottomSheet(
            hardwareList = uiState.hardwareList,
            selectedHardware = uiState.selectedHardware,
            onSelectCamera = { viewModel.selectCamera(it) },
            onDismiss = { viewModel.closeHardwareDialog() }
        )
    }
}

@Composable
fun PermissionRequestView(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .border(2.dp, AmberGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = "Camera Icon",
                tint = AmberGold,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ProCam T3 Hardware Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "To access the Vivo T3 Camera2 ISP, native image sensors, and multi-frame processing pipeline, camera permission is required.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = AmberGold,
                contentColor = ObsidianBlack
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("grant_permission_button")
        ) {
            Text(
                text = "Grant Camera Permission",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun CameraViewfinderContent(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    var shutterPressed by remember { mutableStateOf(false) }
    var flipRotation by remember { mutableFloatStateOf(0f) }
    val animatedFlipRotation by animateFloatAsState(
        targetValue = flipRotation,
        animationSpec = tween(durationMillis = 350),
        label = "cameraFlip"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // TOP CONTROLS BAR
        TopCameraControlsBar(
            flashMode = uiState.flashMode,
            aspectRatio = uiState.aspectRatio,
            isGridEnabled = uiState.isGridEnabled,
            selectedHardware = uiState.selectedHardware,
            onToggleFlash = { viewModel.toggleFlash() },
            onToggleAspectRatio = { viewModel.toggleAspectRatio() },
            onToggleGrid = { viewModel.toggleGrid() },
            onOpenHardwareSpecs = { viewModel.openHardwareDialog() },
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // VIEWFINDER AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight
                val targetRatio = uiState.aspectRatio.ratio

                val (viewWidth, viewHeight) = if (boxWidth / boxHeight > 1 / targetRatio) {
                    Pair(boxHeight / targetRatio, boxHeight)
                } else {
                    Pair(boxWidth, boxWidth * targetRatio)
                }

                Box(
                    modifier = Modifier
                        .size(width = viewWidth, height = viewHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val xNorm = offset.x / size.width
                                val yNorm = offset.y / size.height
                                viewModel.onFocusTouch(xNorm, yNorm)
                            }
                        }
                ) {
                    // Camera2 Viewfinder Surface
                    AndroidView(
                        factory = { ctx ->
                            AutoFitTextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        viewModel.onSurfaceTextureAvailable(surface, width, height)
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        viewModel.onSurfaceTextureAvailable(surface, width, height)
                                    }

                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        viewModel.onSurfaceTextureDestroyed()
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        update = { view ->
                            val r = uiState.aspectRatio
                            when (r) {
                                CameraAspectRatio.RATIO_4_3 -> view.setAspectRatio(3, 4)
                                CameraAspectRatio.RATIO_16_9 -> view.setAspectRatio(9, 16)
                                CameraAspectRatio.RATIO_1_1 -> view.setAspectRatio(1, 1)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Rule of Thirds Grid Overlay
                    if (uiState.isGridEnabled) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // Vertical lines
                            drawLine(GridLine, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                            drawLine(GridLine, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                            // Horizontal lines
                            drawLine(GridLine, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                            drawLine(GridLine, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                        }
                    }

                    // Focus Point Indicator
                    uiState.focusPoint?.let { (xNorm, yNorm) ->
                        val targetX = (xNorm * viewWidth.value).dp - 32.dp
                        val targetY = (yNorm * viewHeight.value).dp - 32.dp

                        Box(
                            modifier = Modifier
                                .offset(x = targetX, y = targetY)
                                .size(64.dp)
                                .border(1.5.dp, AmberGold, CircleShape)
                        )
                    }

                    // Capture Flash Screen Animation
                    if (uiState.isCapturing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.85f))
                        )
                    }

                    // Viewfinder HUD Badges
                    ViewfinderHudBadges(
                        hardware = uiState.selectedHardware,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }

        // MULTI-CAMERA SELECTOR CHIPS
        if (uiState.hardwareList.size > 1) {
            CameraSelectorRow(
                hardwareList = uiState.hardwareList,
                selectedId = uiState.currentCameraId,
                onSelect = { viewModel.selectCamera(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }

        // BOTTOM CONTROLS (Shutter, Gallery Thumbnail, Camera Switch)
        BottomShutterControls(
            lastCapturedBitmap = uiState.lastCapturedBitmap,
            isCapturing = uiState.isCapturing,
            shutterPressed = shutterPressed,
            flipRotation = animatedFlipRotation,
            onShutterClick = {
                viewModel.takePicture()
            },
            onShutterPressChange = { shutterPressed = it },
            onSwitchCamera = {
                flipRotation += 180f
                viewModel.switchCamera()
            },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun TopCameraControlsBar(
    flashMode: FlashMode,
    aspectRatio: CameraAspectRatio,
    isGridEnabled: Boolean,
    selectedHardware: CameraHardwareInfo?,
    onToggleFlash: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenHardwareSpecs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash Toggle
        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier.testTag("flash_toggle_button")
        ) {
            val (icon, tint) = when (flashMode) {
                FlashMode.OFF -> Pair(Icons.Default.FlashOff, TextSecondary)
                FlashMode.AUTO -> Pair(Icons.Default.FlashAuto, AmberGold)
                FlashMode.ON -> Pair(Icons.Default.FlashOn, AmberGold)
                FlashMode.TORCH -> Pair(Icons.Default.Highlight, CyanAccent)
            }
            Icon(imageVector = icon, contentDescription = "Flash mode: $flashMode", tint = tint)
        }

        // Aspect Ratio Toggle Button
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.clickable { onToggleAspectRatio() }
        ) {
            Text(
                text = aspectRatio.label,
                color = AmberGoldLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Grid Toggle
        IconButton(
            onClick = onToggleGrid,
            modifier = Modifier.testTag("grid_toggle_button")
        ) {
            Icon(
                imageVector = if (isGridEnabled) Icons.Default.Grid3x3 else Icons.Default.GridOff,
                contentDescription = "Toggle Grid",
                tint = if (isGridEnabled) AmberGold else TextSecondary
            )
        }

        // Hardware Specs Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.clickable { onOpenHardwareSpecs() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Hardware Info",
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val mpText = selectedHardware?.let {
                    String.format(Locale.US, "%.1fMP", it.sensorMegaPixels)
                } ?: "HW"
                Text(
                    text = mpText,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ViewfinderHudBadges(
    hardware: CameraHardwareInfo?,
    modifier: Modifier = Modifier
) {
    if (hardware == null) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianBlack.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${hardware.facing.name} [ID:${hardware.cameraId}]",
                color = CyanAccent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = hardware.hardwareLevel.split(" ").first(),
                color = AmberGoldLight,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            if (hardware.supportsRaw) {
                Text(
                    text = "RAW",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CameraSelectorRow(
    hardwareList: List<CameraHardwareInfo>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(hardwareList) { info ->
            val isSelected = info.cameraId == selectedId
            val label = when (info.facing) {
                LensFacingType.BACK -> "Rear #${info.cameraId} (${String.format(Locale.US, "%.0fMP", info.sensorMegaPixels)})"
                LensFacingType.FRONT -> "Front #${info.cameraId} (${String.format(Locale.US, "%.0fMP", info.sensorMegaPixels)})"
                else -> "Cam #${info.cameraId}"
            }

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(info.cameraId) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberGold,
                    selectedLabelColor = ObsidianBlack,
                    containerColor = DarkSurfaceElevated,
                    labelColor = TextSecondary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun BottomShutterControls(
    lastCapturedBitmap: android.graphics.Bitmap?,
    isCapturing: Boolean,
    shutterPressed: Boolean,
    flipRotation: Float,
    onShutterClick: () -> Unit,
    onShutterPressChange: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreviewDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery / Last Photo Thumbnail
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .border(1.5.dp, if (lastCapturedBitmap != null) AmberGold else Color.DarkGray, CircleShape)
                .clickable {
                    if (lastCapturedBitmap != null) {
                        showPreviewDialog = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = lastCapturedBitmap.asImageBitmap(),
                    contentDescription = "Last captured photo thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Shutter Button
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .border(3.5.dp, AmberGold, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (shutterPressed) AmberGoldLight else AmberGold)
                .clickable(enabled = !isCapturing) {
                    onShutterClick()
                }
                .testTag("shutter_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (shutterPressed) 48.dp else 56.dp)
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.15f))
            )
        }

        // Camera Switch Flip Button
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .clickable { onSwitchCamera() }
                .testTag("switch_camera_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = AmberGold,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(flipRotation)
            )
        }
    }

    // Fullscreen Image Preview Dialog
    if (showPreviewDialog && lastCapturedBitmap != null) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("Close", color = AmberGold)
                }
            },
            title = {
                Text("Last Captured Photo", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = lastCapturedBitmap.asImageBitmap(),
                        contentDescription = "Captured image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareSpecsBottomSheet(
    hardwareList: List<CameraHardwareInfo>,
    selectedHardware: CameraHardwareInfo?,
    onSelectCamera: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hardware Inspector",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )
                Text(
                    text = "${hardwareList.size} sensors detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Camera selector pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(hardwareList) { info ->
                    val isSelected = info.cameraId == selectedHardware?.cameraId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCamera(info.cameraId) },
                        label = {
                            Text("ID ${info.cameraId} (${info.facing.name})")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = ObsidianBlack,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedHardware != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SpecSectionCard(title = "Sensor & Hardware Level") {
                            SpecRow("Camera ID", selectedHardware.cameraId)
                            SpecRow("Lens Facing", selectedHardware.facing.name)
                            SpecRow("Hardware Level", selectedHardware.hardwareLevel)
                            SpecRow(
                                "Sensor Megapixels",
                                String.format(Locale.US, "%.2f MP", selectedHardware.sensorMegaPixels)
                            )
                            selectedHardware.activeArraySize?.let {
                                SpecRow("Active Array", "${it.width()} × ${it.height()} px")
                            }
                            SpecRow("Sensor Orientation", "${selectedHardware.sensorOrientation}°")
                        }
                    }

                    item {
                        SpecSectionCard(title = "Exposure & Sensitivity Controls") {
                            selectedHardware.isoRange?.let {
                                SpecRow("ISO Sensitivity Range", "${it.lower} – ${it.upper}")
                            }
                            selectedHardware.exposureTimeRangeNs?.let {
                                val minMs = it.lower / 1_000_000.0
                                val maxSec = it.upper / 1_000_000_000.0
                                SpecRow("Exposure Time", String.format(Locale.US, "%.3fms – %.2fs", minMs, maxSec))
                            }
                            selectedHardware.exposureCompensationRange?.let {
                                SpecRow("EV Comp Range", "${it.lower}..${it.upper} (step ${selectedHardware.exposureCompensationStep})")
                            }
                            SpecRow("Flash Unit", if (selectedHardware.hasFlashUnit) "Available" else "Not Available")
                        }
                    }

                    item {
                        SpecSectionCard(title = "Pro Pipeline & Stabilization") {
                            SpecRow("RAW / DNG Output", if (selectedHardware.supportsRaw) "Supported" else "Not Supported")
                            SpecRow("Optical Stabilization (OIS)", if (selectedHardware.supportsOis) "Supported" else "Not Supported")
                            SpecRow("Video Stabilization (EIS)", if (selectedHardware.supportsEis) "Supported" else "Not Supported")
                            SpecRow("Max JPEG Resolution", selectedHardware.supportedJpegSizes.firstOrNull()?.let { "${it.width} × ${it.height}" } ?: "N/A")
                            SpecRow("Max Preview Resolution", selectedHardware.supportedPreviewSizes.firstOrNull()?.let { "${it.width} × ${it.height}" } ?: "N/A")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSurfaceElevated,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Inspector")
            }
        }
    }
}

@Composable
fun SpecSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AmberGoldLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
