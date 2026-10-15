package org.remmeasure.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.remmeasure.ui.theme.ReticleLocked
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Operating mode for the digital bubble level:
 * - SURFACE: Phone lies mostly flat on a table, shelf, or floor (2D Bullseye bubble).
 * - EDGE: Phone stands upright on an edge or against a vertical wall (Spirit / Plumb level).
 */
enum class LevelMode {
    SURFACE,
    EDGE
}

/**
 * Resting edge orientation when device is upright in EDGE mode.
 * Rotation degrees rotate UI elements so they remain upright to the user.
 */
enum class DeviceEdge(val rotationDegrees: Float) {
    PORTRAIT_UPRIGHT(0f),
    LANDSCAPE_LEFT(90f),    // Phone resting on left edge (right edge pointing UP)
    LANDSCAPE_RIGHT(-90f),  // Phone resting on right edge (left edge pointing UP)
    PORTRAIT_INVERTED(180f) // Phone resting on top edge (bottom edge pointing UP)
}

/**
 * 8-Lobed Scalloped Organic Shape matching Material 3 Expressive Lab geometry.
 */
class ScallopShape(
    private val lobes: Int = 8,
    private val depth: Float = 0.11f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = min(cx, cy) / (1f + depth)
        val steps = 180

        for (i in 0 until steps) {
            val angle = (i * 2.0 * PI / steps).toFloat()
            val r = baseR * (1f + depth * cos(lobes * angle))
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun LevelScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Normalized UP direction vector in device coordinate frame:
    // gx: +right, gy: +up (top edge), gz: +out of screen (towards user)
    var rawGx by remember { mutableFloatStateOf(0f) }
    var rawGy by remember { mutableFloatStateOf(0f) }
    var rawGz by remember { mutableFloatStateOf(1f) }

    // Calibration offsets
    var surfaceOffsetDeg by remember { mutableFloatStateOf(0f) }
    var edgeOffsetDeg by remember { mutableFloatStateOf(0f) }

    // Control toggles
    var isLocked by remember { mutableStateOf(false) }

    // Connect to hardware accelerometer / rotation vector
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private var filteredGx = 0f
            private var filteredGy = 0f
            private var filteredGz = 1f

            override fun onSensorChanged(event: SensorEvent?) {
                if (isLocked || event == null) return

                var currentGx = 0f
                var currentGy = 0f
                var currentGz = 1f

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    // The 3rd row of the rotation matrix represents the world UP vector
                    // expressed in the device's local coordinate system:
                    currentGx = rotationMatrix[6]
                    currentGy = rotationMatrix[7]
                    currentGz = rotationMatrix[8]
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val norm = sqrt(ax * ax + ay * ay + az * az)
                    if (norm > 0.001f) {
                        currentGx = ax / norm
                        currentGy = ay / norm
                        currentGz = az / norm
                    }
                }

                // Low-pass filter for butter-smooth readings
                filteredGx = filteredGx * 0.80f + currentGx * 0.20f
                filteredGy = filteredGy * 0.80f + currentGy * 0.20f
                filteredGz = filteredGz * 0.80f + currentGz * 0.20f

                val len = sqrt(filteredGx * filteredGx + filteredGy * filteredGy + filteredGz * filteredGz)
                if (len > 0.001f) {
                    rawGx = filteredGx / len
                    rawGy = filteredGy / len
                    rawGz = filteredGz / len
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // AUTOMATIC MODE DETECTION WITH HYSTERESIS
    var currentMode by remember { mutableStateOf(LevelMode.SURFACE) }
    val absGz = abs(rawGz)
    if (currentMode == LevelMode.SURFACE && absGz < 0.65f) {
        currentMode = LevelMode.EDGE
    } else if (currentMode == LevelMode.EDGE && absGz > 0.75f) {
        currentMode = LevelMode.SURFACE
    }

    // RESTING EDGE ORIENTATION DETECTION WITH HYSTERESIS (Edge Mode)
    var currentEdge by remember { mutableStateOf(DeviceEdge.PORTRAIT_UPRIGHT) }
    if (currentMode == LevelMode.EDGE) {
        val absX = abs(rawGx)
        val absY = abs(rawGy)
        val isLandscape = currentEdge == DeviceEdge.LANDSCAPE_LEFT || currentEdge == DeviceEdge.LANDSCAPE_RIGHT
        val bias = 0.15f
        val chooseLandscape = if (isLandscape) absX > (absY - bias) else absX > (absY + bias)

        currentEdge = if (chooseLandscape) {
            if (rawGx >= 0f) DeviceEdge.LANDSCAPE_LEFT else DeviceEdge.LANDSCAPE_RIGHT
        } else {
            if (rawGy >= 0f) DeviceEdge.PORTRAIT_UPRIGHT else DeviceEdge.PORTRAIT_INVERTED
        }
    } else {
        currentEdge = DeviceEdge.PORTRAIT_UPRIGHT
    }

    // Reset edge calibration offset if resting edge changes
    var lastEdge by remember { mutableStateOf(currentEdge) }
    if (currentEdge != lastEdge) {
        edgeOffsetDeg = 0f
        lastEdge = currentEdge
    }

    // Shortest angular path tracking for continuous smooth virtual rotation
    var continuousTargetRotation by remember { mutableFloatStateOf(0f) }
    val targetRotation = if (currentMode == LevelMode.EDGE) currentEdge.rotationDegrees else 0f

    LaunchedEffect(targetRotation) {
        var delta = (targetRotation - continuousTargetRotation) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        continuousTargetRotation += delta
    }

    val animatedRotation by animateFloatAsState(
        targetValue = continuousTargetRotation,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "VirtualRotation"
    )

    // ANGLE CALCULATIONS ACCORDING TO MODE & ACTIVE EDGE
    // 1. Surface Mode: Deviation of phone surface normal from vertical
    val rawSurfaceAngle = Math.toDegrees(acos(absGz.coerceIn(0f, 1f)).toDouble()).toFloat()
    val calibratedSurfaceAngle = max(0f, rawSurfaceAngle - surfaceOffsetDeg)

    // 2. Edge Mode: Calculate tilt along the active resting edge
    val (tiltX, tiltY) = when (currentEdge) {
        DeviceEdge.PORTRAIT_UPRIGHT -> rawGx to rawGy
        DeviceEdge.LANDSCAPE_LEFT -> -rawGy to rawGx
        DeviceEdge.LANDSCAPE_RIGHT -> rawGy to -rawGx
        DeviceEdge.PORTRAIT_INVERTED -> -rawGx to -rawGy
    }
    val rawEdgeAngle = Math.toDegrees(atan2(tiltX.toDouble(), tiltY.toDouble())).toFloat()
    val calibratedEdgeAngle = rawEdgeAngle - edgeOffsetDeg

    // Animate smoothly
    val animatedSurfaceAngle by animateFloatAsState(
        targetValue = calibratedSurfaceAngle,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "SurfaceAngle"
    )
    val animatedEdgeAngle by animateFloatAsState(
        targetValue = calibratedEdgeAngle,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "EdgeAngle"
    )
    val animatedGx by animateFloatAsState(
        targetValue = rawGx,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "VectorGx"
    )
    val animatedGy by animateFloatAsState(
        targetValue = rawGy,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "VectorGy"
    )

    // Check if within level threshold (0.5 degrees)
    val isLeveled = if (currentMode == LevelMode.SURFACE) {
        animatedSurfaceAngle <= 0.5f
    } else {
        abs(animatedEdgeAngle) <= 0.5f
    }

    // Trigger haptic tick on level entry
    var wasLeveled by remember { mutableStateOf(false) }
    LaunchedEffect(isLeveled) {
        if (isLeveled && !wasLeveled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasLeveled = isLeveled
    }

    // Dynamic color morphing between active tilted (cyan/blue) and leveled (lavender/purple)
    val isDark = isSystemInDarkTheme()
    val skyColorTop by animateColorAsState(
        targetValue = if (isLeveled) {
            Color(0xFFEDE7F6)
        } else if (isDark) {
            MaterialTheme.colorScheme.surface
        } else {
            Color(0xFFD4EEFC)
        },
        animationSpec = tween(350),
        label = "SkyColor"
    )
    val skyColorBottom by animateColorAsState(
        targetValue = if (isLeveled) {
            Color(0xFFD1C4E9)
        } else if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color(0xFFB3E5FC)
        },
        animationSpec = tween(350),
        label = "SkyBottomColor"
    )
    val liquidColor by animateColorAsState(
        targetValue = if (isLeveled) {
            Color(0xFFB39DDB)
        } else if (isDark) {
            MaterialTheme.colorScheme.primary
        } else {
            Color(0xFF00A2FF)
        },
        animationSpec = tween(350),
        label = "LiquidColor"
    )
    val bubbleColor by animateColorAsState(
        targetValue = if (isLeveled) {
            Color(0xFF5E35B1)
        } else if (isDark) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color(0xFF0D47A1).copy(alpha = 0.85f)
        },
        animationSpec = tween(350),
        label = "BubbleColor"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isLeveled) {
            Color(0xFF5E35B1).copy(alpha = 0.65f)
        } else {
            Color.White.copy(alpha = 0.55f)
        },
        animationSpec = tween(350),
        label = "RingColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isLeveled) Color(0xFF311B92) else if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0A3055),
        animationSpec = tween(280),
        label = "TextColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skyColorTop, skyColorBottom)))
    ) {
        // BACKGROUND FLUID HORIZON (Active in Edge mode - rotates continuously with gravity)
        if (currentMode == LevelMode.EDGE) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val cx = canvasW / 2f
                val cy = canvasH * 0.5f

                // In Canvas coordinates, y=0 is top, so canvasUpY is -animatedGy
                val upX = animatedGx
                val upY = -animatedGy
                val len = sqrt(upX * upX + upY * upY).coerceAtLeast(0.001f)
                val nx = upX / len
                val ny = upY / len

                // Vector parallel to fluid surface (perpendicular to up)
                val px = -ny
                val py = nx

                val span = max(canvasW, canvasH) * 1.5f
                val depth = max(canvasW, canvasH) * 1.5f

                // Liquid surface endpoints
                val p1 = Offset(cx - px * span, cy - py * span)
                val p2 = Offset(cx + px * span, cy + py * span)

                // Liquid body extends toward gravity (-nx, -ny)
                val p3 = Offset(p2.x - nx * depth, p2.y - ny * depth)
                val p4 = Offset(p1.x - nx * depth, p1.y - ny * depth)

                // Translucent meniscus wave layer (offset slightly toward sky (+nx, +ny))
                val meniscusOffset = 18f
                val meniscusPath = Path().apply {
                    moveTo(p1.x + nx * meniscusOffset, p1.y + ny * meniscusOffset)
                    lineTo(p2.x + nx * meniscusOffset, p2.y + ny * meniscusOffset)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(path = meniscusPath, color = liquidColor.copy(alpha = 0.25f))

                // Primary liquid body
                val liquidPath = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(path = liquidPath, color = liquidColor.copy(alpha = 0.70f))
            }
        }

        // TOP HEADER: Title & Mode Indicator Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Level",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Badge (Surface vs Edge with edge state)
            val modeLabel = if (currentMode == LevelMode.SURFACE) {
                "Surface Mode (Flat)"
            } else {
                when (currentEdge) {
                    DeviceEdge.PORTRAIT_UPRIGHT -> "Edge Mode (Portrait)"
                    DeviceEdge.LANDSCAPE_LEFT, DeviceEdge.LANDSCAPE_RIGHT -> "Edge Mode (Landscape)"
                    DeviceEdge.PORTRAIT_INVERTED -> "Edge Mode (Inverted)"
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isLeveled) bubbleColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (currentMode == LevelMode.SURFACE) Icons.Rounded.Layers else Icons.Rounded.Architecture,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
        }

        // CENTER GAUGE DISPLAY
        val displayedAngle = if (currentMode == LevelMode.SURFACE) animatedSurfaceAngle else abs(animatedEdgeAngle)
        val formattedAngle = if (isLeveled) "0,0°" else String.format(Locale.GERMANY, "%.1f°", displayedAngle)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (currentMode == LevelMode.SURFACE) {
                // SURFACE MODE: Degree readout + 2D Bullseye & Scalloped Floating Bubble
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedAngle,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                        fontWeight = FontWeight.Black,
                        color = textColor,
                        letterSpacing = (-1.5).sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val targetSize = 200.dp
                    val bubbleSize = 120.dp
                    val scallopShape = remember { ScallopShape(lobes = 8, depth = 0.12f) }

                    val maxTravelPx = with(LocalDensity.current) { 90.dp.toPx() }
                    // Physical bubble floats towards high point (+gx moves right, +gy moves up/-Y on screen)
                    val bubbleOffsetX = (animatedGx * maxTravelPx * 2.2f).coerceIn(-maxTravelPx, maxTravelPx)
                    val bubbleOffsetY = (-animatedGy * maxTravelPx * 2.2f).coerceIn(-maxTravelPx, maxTravelPx)

                    Box(
                        modifier = Modifier.size(targetSize),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer Bullseye Target Outline
                        Box(
                            modifier = Modifier
                                .size(targetSize)
                                .clip(scallopShape)
                                .border(width = 4.dp, color = ringColor, shape = scallopShape)
                        )

                        // Inner Bullseye Target Circle (Dead center snap target)
                        Canvas(modifier = Modifier.size(targetSize)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                color = ringColor.copy(alpha = 0.5f),
                                radius = 40.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            val tickLen = 14.dp.toPx()
                            drawLine(
                                color = ringColor.copy(alpha = 0.7f),
                                start = Offset(center.x - tickLen, center.y),
                                end = Offset(center.x + tickLen, center.y),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = ringColor.copy(alpha = 0.7f),
                                start = Offset(center.x, center.y - tickLen),
                                end = Offset(center.x, center.y + tickLen),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // Floating 2D Bubble
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = if (isLeveled) 0 else bubbleOffsetX.roundToInt(),
                                        y = if (isLeveled) 0 else bubbleOffsetY.roundToInt()
                                    )
                                }
                                .size(bubbleSize)
                                .clip(scallopShape)
                                .background(bubbleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLeveled) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Leveled",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // EDGE MODE: Rotates smoothly with animatedRotation (Portrait, Landscape Left, Landscape Right, Inverted)
                Column(
                    modifier = Modifier.graphicsLayer {
                        rotationZ = animatedRotation
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedAngle,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                        fontWeight = FontWeight.Black,
                        color = textColor,
                        letterSpacing = (-1.5).sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    val tubeWidth = 290.dp
                    val tubeHeight = 84.dp
                    val bubbleDiameter = 68.dp
                    val tubeShape = RoundedCornerShape(42.dp)

                    val maxTravelPx = with(LocalDensity.current) { 105.dp.toPx() }
                    val bubbleOffsetX = ((animatedEdgeAngle / 30f) * maxTravelPx).coerceIn(-maxTravelPx, maxTravelPx)

                    Box(
                        modifier = Modifier
                            .size(width = tubeWidth, height = tubeHeight)
                            .clip(tubeShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(width = 3.5.dp, color = ringColor, shape = tubeShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center Goal Markers (Goal posts for leveling)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val topY = 12.dp.toPx()
                            val botY = size.height - 12.dp.toPx()
                            val markerSpacing = 38.dp.toPx()

                            // Left goal post
                            drawLine(
                                color = ringColor.copy(alpha = 0.85f),
                                start = Offset(cx - markerSpacing, topY),
                                end = Offset(cx - markerSpacing, botY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Right goal post
                            drawLine(
                                color = ringColor.copy(alpha = 0.85f),
                                start = Offset(cx + markerSpacing, topY),
                                end = Offset(cx + markerSpacing, botY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Center dot
                            drawCircle(
                                color = ringColor.copy(alpha = 0.7f),
                                radius = 3.dp.toPx(),
                                center = Offset(cx, size.height / 2f)
                            )
                        }

                        // Sliding Bubble within Spirit Tube
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = if (isLeveled) 0 else bubbleOffsetX.roundToInt(),
                                        y = 0
                                    )
                                }
                                .size(bubbleDiameter)
                                .clip(CircleShape)
                                .background(bubbleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLeveled) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Leveled",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM CONTROLS (Lock / Hold & Zero Calibrate)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zero / Calibrate Button
            val isCalibrated = if (currentMode == LevelMode.SURFACE) surfaceOffsetDeg != 0f else edgeOffsetDeg != 0f
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCalibrated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isCalibrated) {
                            // Reset calibration
                            if (currentMode == LevelMode.SURFACE) surfaceOffsetDeg = 0f else edgeOffsetDeg = 0f
                        } else {
                            // Zero to current angle
                            if (currentMode == LevelMode.SURFACE) {
                                surfaceOffsetDeg = rawSurfaceAngle
                            } else {
                                edgeOffsetDeg = rawEdgeAngle
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = if (isCalibrated) "Reset Calibration" else "Zero Angle",
                    tint = if (isCalibrated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = animatedRotation }
                )
            }

            // Lock / Freeze Button
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
            val buttonColor = if (isLocked) MaterialTheme.colorScheme.surfaceContainerHighest else primaryColor
            val iconTint = if (isLocked) Color(0xFFE53935) else onPrimaryColor

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable {
                        isLocked = !isLocked
                        hapticFeedback.performHapticFeedback(
                            if (isLocked) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (isLocked) "Unlock angle" else "Lock angle",
                    tint = iconTint,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = animatedRotation }
                )
            }
        }
    }
}
