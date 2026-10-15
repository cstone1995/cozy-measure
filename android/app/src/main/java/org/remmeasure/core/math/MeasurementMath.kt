package org.remmeasure.core.math

import org.remmeasure.core.model.MeasurementUnit
import org.remmeasure.core.model.Vector3
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object MeasurementMath {

    /**
     * Formats distance in meters into a user-friendly string based on the chosen unit.
     */
    fun formatDistance(meters: Float, unit: MeasurementUnit): String {
        if (meters <= 0f) return "0.0 cm"
        return when (unit) {
            MeasurementUnit.METRIC -> {
                if (meters >= 1.0f) {
                    val m = meters.toInt()
                    val cm = ((meters - m) * 100f).roundToInt()
                    if (cm == 100) {
                        String.format(Locale.US, "%d m", m + 1)
                    } else if (cm > 0) {
                        String.format(Locale.US, "%d m %d cm", m, cm)
                    } else {
                        String.format(Locale.US, "%d m", m)
                    }
                } else {
                    val cm = (meters * 100f)
                    if (cm < 1.0f) {
                        String.format(Locale.US, "%.1f mm", meters * 1000f)
                    } else {
                        String.format(Locale.US, "%.1f cm", cm)
                    }
                }
            }
            MeasurementUnit.IMPERIAL -> {
                val totalInches = meters * 39.3701f
                val feet = (totalInches / 12).toInt()
                val inches = (totalInches % 12).roundToInt()
                if (feet > 0) {
                    if (inches == 12) {
                        String.format(Locale.US, "%d ft", feet + 1)
                    } else if (inches > 0) {
                        String.format(Locale.US, "%d ft %d in", feet, inches)
                    } else {
                        String.format(Locale.US, "%d ft", feet)
                    }
                } else {
                    String.format(Locale.US, "%d in", inches)
                }
            }
            MeasurementUnit.INCHES -> {
                val inches = meters * 39.3701f
                String.format(Locale.US, "%.1f in", inches)
            }
        }
    }

    /**
     * Snaps a candidate 3D position [candidate] to a strict axis (vertical Y or orthogonal X/Z)
     * originating from [origin] if within [distanceThresholdMeters].
     * Returns the snapped position, or [candidate] untouched if outside tolerance.
     */
    fun snapToAxisIfClose(
        origin: Vector3,
        candidate: Vector3,
        distanceThresholdMeters: Float = 0.035f
    ): Vector3 {
        val diff = candidate - origin
        val len = diff.length()
        if (len < 1e-4f) return candidate

        // 1. Check for vertical alignment (along vertical Y axis)
        val horizontalDist = Vector3(diff.x, 0f, diff.z).length()
        if (horizontalDist < distanceThresholdMeters) {
            return Vector3(origin.x, candidate.y, origin.z)
        }

        // 2. Check for horizontal level alignment (along horizontal plane Y = origin.y)
        if (abs(diff.y) < distanceThresholdMeters) {
            // Check for orthogonal X-axis alignment (parallel to X axis, diff.z ≈ 0)
            if (abs(diff.z) < distanceThresholdMeters) {
                return Vector3(candidate.x, origin.y, origin.z)
            }
            // Check for orthogonal Z-axis alignment (parallel to Z axis, diff.x ≈ 0)
            if (abs(diff.x) < distanceThresholdMeters) {
                return Vector3(origin.x, origin.y, candidate.z)
            }
            // Snap to horizontal level
            return Vector3(candidate.x, origin.y, candidate.z)
        }

        return candidate
    }

    /**
     * Snaps candidate point to an existing endpoint if within [snapRadiusMeters].
     */
    fun snapToExistingPoint(
        candidate: Vector3,
        points: List<Vector3>,
        snapRadiusMeters: Float = 0.05f
    ): Vector3? {
        var closestPoint: Vector3? = null
        var minDistance = snapRadiusMeters

        for (point in points) {
            val dist = candidate.distanceTo(point)
            if (dist < minDistance) {
                minDistance = dist
                closestPoint = point
            }
        }
        return closestPoint
    }

    /**
     * Checks if the camera's sight ray is pointing near the vertical line originating from [startPoint].
     * If within [snapThresholdMeters], returns the 3D position snapped to the vertical line (startPoint.x, heightY, startPoint.z).
     */
    fun checkVerticalSnap(
        startPoint: Vector3,
        cameraOrigin: Vector3,
        cameraRayDir: Vector3,
        snapThresholdMeters: Float = 0.15f,
        maxHeightMeters: Float = 3.0f
    ): Vector3? {
        val dx = cameraRayDir.x
        val dz = cameraRayDir.z
        val horizDirLenSq = dx * dx + dz * dz
        if (horizDirLenSq < 1e-4f) return null

        val vx = startPoint.x - cameraOrigin.x
        val vz = startPoint.z - cameraOrigin.z

        val s = (vx * dx + vz * dz) / horizDirLenSq
        if (s <= 0.1f) return null

        val rayX = cameraOrigin.x + s * dx
        val rayY = cameraOrigin.y + s * cameraRayDir.y
        val rayZ = cameraOrigin.z + s * dz

        val distX = rayX - startPoint.x
        val distZ = rayZ - startPoint.z
        val horizontalDistance = kotlin.math.sqrt(distX * distX + distZ * distZ)

        if (horizontalDistance <= snapThresholdMeters) {
            val heightFromStart = rayY - startPoint.y
            if (heightFromStart in 0.02f..maxHeightMeters) {
                return Vector3(startPoint.x, rayY, startPoint.z)
            }
        }
        return null
    }
}
