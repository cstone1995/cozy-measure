package org.remmeasure.core.model

import kotlin.math.sqrt

/**
 * Immutable 3D vector representation with standard linear algebra helpers.
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun distanceTo(other: Vector3): Float = (this - other).length()

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 1e-6f) this / len else Zero
    }

    companion object {
        val Zero = Vector3(0f, 0f, 0f)
        val Up = Vector3(0f, 1f, 0f)
        val Forward = Vector3(0f, 0f, -1f)
        val Right = Vector3(1f, 0f, 0f)
    }
}
