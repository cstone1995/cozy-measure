package org.remmeasure.core.model

import java.util.UUID

/**
 * Represents a single measured 3D line segment between two anchored points.
 */
data class MeasurementSegment(
    val id: String = UUID.randomUUID().toString(),
    val start: Vector3,
    val end: Vector3,
    val distanceMeters: Float = start.distanceTo(end),
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true
) {
    val midpoint: Vector3 get() = (start + end) * 0.5f
}
