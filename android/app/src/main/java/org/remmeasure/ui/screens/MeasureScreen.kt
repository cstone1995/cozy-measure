package org.remmeasure.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import org.remmeasure.core.math.TargetStability
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARScene
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import org.remmeasure.core.CameraSnapshotHelper
import org.remmeasure.core.math.MeasurementMath
import org.remmeasure.core.model.TrackingStatus
import org.remmeasure.core.model.Vector3
import org.remmeasure.ui.components.BottomActionBar
import org.remmeasure.ui.components.MeasurementDetailSheet
import org.remmeasure.ui.components.ReticleOverlay
import org.remmeasure.ui.components.StatusBanner
import org.remmeasure.ui.components.TopBarControls
import org.remmeasure.ui.theme.SurfaceDark
import org.remmeasure.ui.theme.TextLight
import org.remmeasure.ui.theme.TextMuted
import org.remmeasure.ui.viewmodel.MeasureViewModel
import kotlin.math.roundToInt

private val GoogleYellow = Color(0xFFFBBB04)

@Composable
fun MeasureScreen(
    viewModel: MeasureViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onReviewRoom: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun triggerHaptic(type: String = "tick") {
        if (!uiState.isVibrationEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    "click" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    "heavy" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    "snap" -> VibrationEffect.createOneShot(25, 200)
                    else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    if (!hasCameraPermission) {
        CameraPermissionFallback(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        })
        return
    }

    LaunchedEffect(Unit){if(viewModel.uiState.value.roomCorners.isNotEmpty()&&!viewModel.uiState.value.roomClosed)viewModel.clearAllMeasurements()}
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val screenWidthPx=viewport.width.toFloat()
    val screenHeightPx=viewport.height.toFloat()
    val cornerAnchors=remember{mutableListOf<Anchor>()}
    val stability=remember{TargetStability()}
    var warmFrames by remember{mutableStateOf(0)}
    var captureRequested by remember{mutableStateOf(false)}
    var anchorsTracking by remember{mutableStateOf(true)}
    DisposableEffect(Unit){onDispose{cornerAnchors.forEach{it.detach()};cornerAnchors.clear()}}

    // Camera view and projection matrices updated each AR frame for world-to-screen projection
    var cameraViewMatrix by remember { mutableStateOf(FloatArray(16)) }
    var cameraProjMatrix by remember { mutableStateOf(FloatArray(16)) }
    var frameUpdateCounter by remember { mutableStateOf(0L) }

    // Real-time detected plane pose for world-anchored sliding plane dots
    var candidatePlanePose by remember { mutableStateOf<Pose?>(null) }

    // Haptic feedback when magnetically snapping to a point or axis
    LaunchedEffect(uiState.isSnappedToPoint, uiState.isSnappedToAxis) {
        if (uiState.isSnappedToPoint || uiState.isSnappedToAxis) {
            triggerHaptic("snap")
        }
    }

    // Camera shutter flash state
    var isFlashing by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.85f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "camera_flash",
        finishedListener = { if (isFlashing) isFlashing = false }
    )

    val engine = rememberEngine()
    val childNodes = remember { emptyList<Node>() }

    val isMeasuring = uiState.currentStartPoint != null

    Box(modifier = modifier.fillMaxSize().onSizeChanged{viewport=it}) {

        // 1. AR Scene Layer - camera stream and surface tracking
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            childNodes = childNodes,
            planeRenderer = false,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
            },
            onSessionUpdated = { _, frame ->
                val current=viewModel.uiState.value
                while(cornerAnchors.size>current.roomCorners.size){cornerAnchors.removeAt(cornerAnchors.lastIndex).detach();stability.reset();warmFrames=0}
                anchorsTracking=cornerAnchors.all{it.trackingState==TrackingState.TRACKING}
                if(anchorsTracking && !current.roomClosed)viewModel.updateTrackedCorners(cornerAnchors.map{val p=it.pose;Vector3(p.tx(),p.ty(),p.tz())})
                val camera = frame.camera
                if (camera.trackingState == TrackingState.TRACKING && anchorsTracking && viewport.width>0 && viewport.height>0) {
                    warmFrames++
                    val vMat = FloatArray(16)
                    val pMat = FloatArray(16)
                    camera.getViewMatrix(vMat, 0)
                    camera.getProjectionMatrix(pMat, 0, 0.1f, 100f)
                    cameraViewMatrix = vMat
                    cameraProjMatrix = pMat
                    frameUpdateCounter++

                    // 2. Otherwise perform standard surface raycast
                    run {
                        performCenterRaycast(
                            frame = frame,
                            screenX = screenWidthPx / 2f,
                            screenY = screenHeightPx / 2f,
                            onHit = { hitVector, hitPlanePose, hit ->
                                candidatePlanePose = hitPlanePose
                                val stable=stability.update(hitVector,frame.timestamp)
                                if(warmFrames<45||!stable){
                                    viewModel.invalidateRoomTarget("Hold the target still on the floor corner for a moment.")
                                    captureRequested=false
                                } else {
                                    viewModel.onSurfaceHit(hitVector, isVerticalSnap = false)
                                    if(captureRequested){
                                        captureRequested=false
                                        val anchor=runCatching{hit.createAnchor()}.getOrNull()
                                        if(anchor!=null){
                                            if(viewModel.onPrimaryActionButtonClicked()){cornerAnchors.add(anchor);stability.reset();triggerHaptic("snap")}
                                            else anchor.detach()
                                        } else viewModel.invalidateRoomTarget("Tracking changed. Hold still and try this corner again.")
                                    }
                                }
                            },
                            onNoHit = {
                                candidatePlanePose = null
                                stability.reset();captureRequested=false
                                viewModel.invalidateRoomTarget("Scan the floor slowly in good light. Aim at the floor beside the wall.")
                            }
                        )
                    }
                } else {
                    warmFrames=0;stability.reset();captureRequested=false
                    viewModel.invalidateRoomTarget("Tracking paused. Move slowly over a well-lit floor.")
                }
            }
        )

        // 2. Google Measure Vector AR Canvas Layer (Lines, Rings, Sliding Plane Dots & Reticle)
        if (frameUpdateCounter > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val candidate = uiState.candidatePoint
                val curPlanePose = candidatePlanePose
                val isSnapped = uiState.isSnappedToPoint || uiState.isSnappedToAxis

                // Visually anchor the reticle to the candidate point in 3D screen space
                val reticleScreenPos = if (candidate != null) {
                    projectWorldToScreen(
                        worldPos = candidate,
                        viewMatrix = cameraViewMatrix,
                        projMatrix = cameraProjMatrix,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    ) ?: Offset(screenWidthPx / 2f, screenHeightPx / 2f)
                } else {
                    Offset(screenWidthPx / 2f, screenHeightPx / 2f)
                }

                // A. World-Anchored Stationary Plane Grid with Circular Spotlight Mask
                if (curPlanePose != null && candidate != null && !uiState.isSnappedToAxis) {
                    val localArr = curPlanePose.inverse().transformPoint(
                        floatArrayOf(candidate.x, candidate.y, candidate.z)
                    )
                    val u0 = localArr[0]
                    val v0 = localArr[2]

                    // Stationary grid on physical plane: 7.5 cm spacing, 24 cm radius circular spotlight
                    val spacing = 0.075f
                    val maxRadius = 0.24f
                    val iMin = kotlin.math.floor((u0 - maxRadius) / spacing).toInt()
                    val iMax = kotlin.math.ceil((u0 + maxRadius) / spacing).toInt()
                    val jMin = kotlin.math.floor((v0 - maxRadius) / spacing).toInt()
                    val jMax = kotlin.math.ceil((v0 + maxRadius) / spacing).toInt()

                    for (i in iMin..iMax) {
                        for (j in jMin..jMax) {
                            val dotU = i * spacing
                            val dotV = j * spacing
                            val du = dotU - u0
                            val dv = dotV - v0
                            val distSq = du * du + dv * dv
                            if (distSq <= maxRadius * maxRadius) {
                                val dist = kotlin.math.sqrt(distSq)
                                val alpha = (1f - (dist / maxRadius) * (dist / maxRadius)) * 0.92f
                                val dotWorldArr = curPlanePose.transformPoint(floatArrayOf(dotU, 0f, dotV))
                                val dotWorld = Vector3(dotWorldArr[0], dotWorldArr[1], dotWorldArr[2])
                                val dotScreen = projectWorldToScreen(
                                    worldPos = dotWorld,
                                    viewMatrix = cameraViewMatrix,
                                    projMatrix = cameraProjMatrix,
                                    screenWidth = screenWidthPx,
                                    screenHeight = screenHeightPx
                                )
                                if (dotScreen != null) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
                                        radius = 3.2.dp.toPx(),
                                        center = dotScreen
                                    )
                                }
                            }
                        }
                    }

                    // 3D Golden Reticle Ring lying flat on the plane surface in perspective
                    val ringRadius = 0.055f // 5.5 cm radius on plane
                    val ringPath = Path()
                    var isFirst = true
                    val ringSteps = 32
                    var allRingPointsValid = true

                    for (k in 0..ringSteps) {
                        val theta = (k % ringSteps) * (2 * Math.PI / ringSteps).toFloat()
                        val du = ringRadius * kotlin.math.cos(theta)
                        val dv = ringRadius * kotlin.math.sin(theta)
                        val ptWorldArr = curPlanePose.transformPoint(floatArrayOf(u0 + du, 0f, v0 + dv))
                        val ptWorld = Vector3(ptWorldArr[0], ptWorldArr[1], ptWorldArr[2])
                        val ptScreen = projectWorldToScreen(
                            worldPos = ptWorld,
                            viewMatrix = cameraViewMatrix,
                            projMatrix = cameraProjMatrix,
                            screenWidth = screenWidthPx,
                            screenHeight = screenHeightPx
                        )
                        if (ptScreen != null) {
                            if (isFirst) {
                                ringPath.moveTo(ptScreen.x, ptScreen.y)
                                isFirst = false
                            } else {
                                ringPath.lineTo(ptScreen.x, ptScreen.y)
                            }
                        } else {
                            allRingPointsValid = false
                            break
                        }
                    }
                    if (allRingPointsValid && !isFirst) {
                        ringPath.close()
                        drawPath(
                            path = ringPath,
                            color = GoogleYellow,
                            style = Stroke(width = 2.8.dp.toPx())
                        )
                    }
                } else if (candidate != null) {
                    // Vertical Snap or Fallback: Horizontal perspective ring at candidate height
                    if (uiState.isSnappedToAxis && uiState.currentStartPoint != null) {
                        val ringRadius = 0.055f
                        val ringPath = Path()
                        var isFirst = true
                        val ringSteps = 24
                        var allValid = true
                        for (k in 0..ringSteps) {
                            val theta = (k % ringSteps) * (2 * Math.PI / ringSteps).toFloat()
                            val ptWorld = Vector3(
                                candidate.x + ringRadius * kotlin.math.cos(theta),
                                candidate.y,
                                candidate.z + ringRadius * kotlin.math.sin(theta)
                            )
                            val ptScreen = projectWorldToScreen(
                                worldPos = ptWorld,
                                viewMatrix = cameraViewMatrix,
                                projMatrix = cameraProjMatrix,
                                screenWidth = screenWidthPx,
                                screenHeight = screenHeightPx
                            )
                            if (ptScreen != null) {
                                if (isFirst) {
                                    ringPath.moveTo(ptScreen.x, ptScreen.y)
                                    isFirst = false
                                } else {
                                    ringPath.lineTo(ptScreen.x, ptScreen.y)
                                }
                            } else {
                                allValid = false
                                break
                            }
                        }
                        if (allValid && !isFirst) {
                            ringPath.close()
                            drawPath(
                                path = ringPath,
                                color = GoogleYellow,
                                style = Stroke(width = 2.8.dp.toPx())
                            )
                        }
                    } else {
                        drawCircle(
                            color = GoogleYellow,
                            radius = 16.dp.toPx(),
                            center = reticleScreenPos,
                            style = Stroke(width = 2.8.dp.toPx())
                        )
                    }
                }

                // Center white dot & snapping accent ring
                if (candidate != null) {
                    if (isSnapped) {
                        drawCircle(
                            color = GoogleYellow.copy(alpha = 0.45f),
                            radius = 23.dp.toPx(),
                            center = reticleScreenPos,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = reticleScreenPos,
                        style = Fill
                    )
                }

                // B. Completed Measurement Segments (Uniform 3.5dp Google Yellow Line + End Rings)
                uiState.measurements.forEach { segment ->
                    val startScreen = projectWorldToScreen(
                        worldPos = segment.start,
                        viewMatrix = cameraViewMatrix,
                        projMatrix = cameraProjMatrix,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    )
                    val endScreen = projectWorldToScreen(
                        worldPos = segment.end,
                        viewMatrix = cameraViewMatrix,
                        projMatrix = cameraProjMatrix,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    )
                    if (startScreen != null && endScreen != null) {
                        drawLine(
                            color = GoogleYellow,
                            start = startScreen,
                            end = endScreen,
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Start point ring
                        drawCircle(
                            color = GoogleYellow,
                            radius = 6.dp.toPx(),
                            center = startScreen,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = startScreen,
                            style = Fill
                        )
                        // End point ring
                        drawCircle(
                            color = GoogleYellow,
                            radius = 6.dp.toPx(),
                            center = endScreen,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = endScreen,
                            style = Fill
                        )
                    }
                }

                // C. Active In-Progress Segment & Vertical Height Guideline
                val activeStart = uiState.currentStartPoint
                if (activeStart != null) {
                    val startScreen = projectWorldToScreen(
                        worldPos = activeStart,
                        viewMatrix = cameraViewMatrix,
                        projMatrix = cameraProjMatrix,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    )

                    // 1. Vertical dashed white line rising straight UP from activeStart
                    var topScreen: Offset? = null
                    for (h in listOf(3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.6f, 0.3f, 0.15f)) {
                        val pt = projectWorldToScreen(
                            worldPos = Vector3(activeStart.x, activeStart.y + h, activeStart.z),
                            viewMatrix = cameraViewMatrix,
                            projMatrix = cameraProjMatrix,
                            screenWidth = screenWidthPx,
                            screenHeight = screenHeightPx,
                            allowOffscreen = true
                        )
                        if (pt != null) {
                            topScreen = pt
                            break
                        }
                    }
                    if (startScreen != null && topScreen != null) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = startScreen,
                            end = topScreen,
                            strokeWidth = 2.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                        )
                    }

                    // 2. Yellow measuring line connecting start point to candidate reticle
                    if (startScreen != null) {
                        drawLine(
                            color = GoogleYellow,
                            start = startScreen,
                            end = reticleScreenPos,
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Start point ring
                        drawCircle(
                            color = GoogleYellow,
                            radius = 6.dp.toPx(),
                            center = startScreen,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = startScreen,
                            style = Fill
                        )
                    }
                }
            }
        }

        // 3. Center Reticle Overlay (Only shown as subtle aiming indicator when searching for surfaces)
        ReticleOverlay(
            isSurfaceDetected = uiState.candidatePoint != null,
            modifier = Modifier.align(Alignment.Center)
        )

        // 4. Floating AR Line Badges Layer (Google Measure [ XX cm ▶ ] pills)
        if (frameUpdateCounter > 0) {
            // Completed Measurement Segment Badges
            uiState.measurements.forEach { segment ->
                val screenPos = projectWorldToScreen(
                    worldPos = segment.midpoint,
                    viewMatrix = cameraViewMatrix,
                    projMatrix = cameraProjMatrix,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx
                )

                if (screenPos != null) {
                    val isSelected = uiState.selectedSegment?.id == segment.id
                    val badgeBg = if (isSelected) MaterialTheme.colorScheme.primary else GoogleYellow
                    val badgeContentColor = if (isSelected) Color.White else Color.Black
                    val distanceStr = MeasurementMath.formatDistance(segment.distanceMeters, uiState.selectedUnit)

                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(
                                        x = (screenPos.x - placeable.width / 2f).roundToInt(),
                                        y = (screenPos.y - placeable.height / 2f).roundToInt()
                                    )
                                }
                            }
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeBg)
                            .clickable {
                                triggerHaptic("click")
                                viewModel.selectSegment(segment)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = distanceStr,
                                color = badgeContentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "View measurement details",
                                tint = badgeContentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Active In-Progress Segment Live Badge
            val activeStart = uiState.currentStartPoint
            val activeCandidate = uiState.candidatePoint
            if (activeStart != null && activeCandidate != null) {
                val liveDist = activeStart.distanceTo(activeCandidate)
                if (liveDist > 0.01f) {
                    val activeMidpoint = (activeStart + activeCandidate) * 0.5f
                    val liveScreenPos = projectWorldToScreen(
                        worldPos = activeMidpoint,
                        viewMatrix = cameraViewMatrix,
                        projMatrix = cameraProjMatrix,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    )

                    if (liveScreenPos != null) {
                        val distanceStr = MeasurementMath.formatDistance(liveDist, uiState.selectedUnit)
                        Box(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(
                                            x = (liveScreenPos.x - placeable.width / 2f).roundToInt(),
                                            y = (liveScreenPos.y - placeable.height / 2f).roundToInt()
                                        )
                                    }
                                }
                                .shadow(6.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(GoogleYellow)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = distanceStr,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Top Bar Controls
        TopBarControls(
            hasMeasurements = uiState.measurements.isNotEmpty(),
            selectedUnit = uiState.selectedUnit,
            onClearAll = {
                triggerHaptic("heavy")
                viewModel.clearAllMeasurements()
            },
            onToggleUnit = {
                val nextUnit = when (uiState.selectedUnit) {
                    org.remmeasure.core.model.MeasurementUnit.METRIC -> org.remmeasure.core.model.MeasurementUnit.IMPERIAL
                    org.remmeasure.core.model.MeasurementUnit.IMPERIAL -> org.remmeasure.core.model.MeasurementUnit.INCHES
                    org.remmeasure.core.model.MeasurementUnit.INCHES -> org.remmeasure.core.model.MeasurementUnit.METRIC
                }
                viewModel.setMeasurementUnit(nextUnit)
                triggerHaptic("tick")
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Column(Modifier.align(Alignment.TopCenter).padding(top=100.dp,start=16.dp,end=16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE303A34)).padding(14.dp)) {
            Text("Cozy room scan · ${uiState.roomCorners.size} corners",color=Color.White,fontWeight=FontWeight.Bold)
            Text(uiState.roomMessage,color=Color.White,fontSize=14.sp)
            Text("1 Find floor  →  2 Add corners around walls  →  3 Finish room",color=Color.White,fontSize=12.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom=95.dp,start=16.dp,end=16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE303A34)).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(uiState.roomClosed) {
                Button(onClick=onReviewRoom) { Text("Review floor plan & carpet size") }
            } else {
                Text(if(uiState.candidatePoint!=null) "Target steady · ready to capture" else "Find the floor corner and hold still",color=Color.White)
                Button(enabled=uiState.candidatePoint!=null,onClick={captureRequested=true}) { Text("Add corner ${uiState.roomCorners.size+1}") }
                if(uiState.roomCorners.size>=3) Button(onClick={if(anchorsTracking && viewModel.closeRoom())onReviewRoom()}){Text("Finish room")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(enabled=uiState.roomCorners.isNotEmpty(),onClick={viewModel.undoRoomCorner()}) {Text("Undo")}
                Button(onClick={viewModel.clearAllMeasurements()}) {Text("Start again")}
            }
        }

        // 8. Camera Shutter Flash Overlay
        if (flashAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }
    }
}

/**
 * Projects a 3D world space coordinate into 2D Compose screen pixel coordinates using
 * ARCore's view and projection matrices.
 */
private fun projectWorldToScreen(
    worldPos: Vector3,
    viewMatrix: FloatArray,
    projMatrix: FloatArray,
    screenWidth: Float,
    screenHeight: Float,
    allowOffscreen: Boolean = false
): Offset? {
    if (viewMatrix.size < 16 || projMatrix.size < 16) return null

    // 1. Multiply viewMatrix (column-major 4x4) * worldPos [x, y, z, 1.0]
    val vx = viewMatrix[0] * worldPos.x + viewMatrix[4] * worldPos.y + viewMatrix[8] * worldPos.z + viewMatrix[12]
    val vy = viewMatrix[1] * worldPos.x + viewMatrix[5] * worldPos.y + viewMatrix[9] * worldPos.z + viewMatrix[13]
    val vz = viewMatrix[2] * worldPos.x + viewMatrix[6] * worldPos.y + viewMatrix[10] * worldPos.z + viewMatrix[14]
    val vw = viewMatrix[3] * worldPos.x + viewMatrix[7] * worldPos.y + viewMatrix[11] * worldPos.z + viewMatrix[15]

    // 2. Multiply projMatrix (column-major 4x4) * eyePos [vx, vy, vz, vw]
    val cx = projMatrix[0] * vx + projMatrix[4] * vy + projMatrix[8] * vz + projMatrix[12] * vw
    val cy = projMatrix[1] * vx + projMatrix[5] * vy + projMatrix[9] * vz + projMatrix[13] * vw
    val cz = projMatrix[2] * vx + projMatrix[6] * vy + projMatrix[10] * vz + projMatrix[14] * vw
    val cw = projMatrix[3] * vx + projMatrix[7] * vy + projMatrix[11] * vz + projMatrix[15] * vw

    // Behind camera or too close to near clipping plane
    if (cw <= 0.05f) return null

    val ndcX = cx / cw
    val ndcY = cy / cw

    // Bounds check with generous margin for smooth badge animation
    if (!allowOffscreen && (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f)) return null

    // Convert normalized device coordinates [-1, 1] to screen pixel coordinates [0, width], [0, height]
    val screenX = (ndcX + 1f) * 0.5f * screenWidth
    val screenY = (1f - ndcY) * 0.5f * screenHeight

    return Offset(screenX, screenY)
}

private fun performCenterRaycast(
    frame: Frame,
    screenX: Float,
    screenY: Float,
    onHit: (Vector3, Pose?, HitResult) -> Unit,
    onNoHit: () -> Unit
) {
    if (frame.camera.trackingState != TrackingState.TRACKING) {
        onNoHit()
        return
    }

    val hitResults = frame.hitTest(screenX, screenY)
    val validHit = hitResults.firstOrNull { hit ->
        val trackable = hit.trackable
        when (trackable) {
            is Plane -> trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING && trackable.isPoseInPolygon(hit.hitPose) && trackable.trackingState == TrackingState.TRACKING
            else -> false
        }
    }

    if (validHit != null) {
        val pose = validHit.hitPose
        val pos = Vector3(pose.tx(), pose.ty(), pose.tz())
        val plane = validHit.trackable as? Plane
        val planePose = plane?.centerPose ?: pose
        onHit(pos, planePose, validHit)
    } else {
        onNoHit()
    }
}

@Composable
fun CameraPermissionFallback(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = TextLight,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Camera Permission Required",
                color = TextLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Cozy Measure needs access to your camera to detect real-world surfaces and compute distances.",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onRequestPermission) {
                Text("Grant Camera Access")
            }
        }
    }
}
