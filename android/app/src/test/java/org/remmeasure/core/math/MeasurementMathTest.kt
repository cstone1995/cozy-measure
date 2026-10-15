package org.remmeasure.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.remmeasure.core.model.MeasurementUnit
import org.remmeasure.core.model.Vector3

class MeasurementMathTest {

    @Test
    fun testVector3Distance() {
        val p1 = Vector3(0f, 0f, 0f)
        val p2 = Vector3(3f, 4f, 0f)
        assertEquals(5.0f, p1.distanceTo(p2), 1e-4f)
    }

    @Test
    fun testFormatDistanceMetric() {
        // Less than a meter (centimeters)
        val cmFormatted = MeasurementMath.formatDistance(0.452f, MeasurementUnit.METRIC)
        assertEquals("45.2 cm", cmFormatted)

        // Greater than a meter
        val mFormatted = MeasurementMath.formatDistance(1.85f, MeasurementUnit.METRIC)
        assertEquals("1 m 85 cm", mFormatted)

        // Exact meters
        val exactM = MeasurementMath.formatDistance(2.00f, MeasurementUnit.METRIC)
        assertEquals("2 m", exactM)
    }

    @Test
    fun testFormatDistanceImperial() {
        // 1 meter is ~39.37 inches -> 3 ft 3 in
        val formatted = MeasurementMath.formatDistance(1.0f, MeasurementUnit.IMPERIAL)
        assertEquals("3 ft 3 in", formatted)

        // Short distance (under 1 foot)
        val shortDist = MeasurementMath.formatDistance(0.15f, MeasurementUnit.IMPERIAL) // ~5.9 in -> 6 in
        assertEquals("6 in", shortDist)
    }

    @Test
    fun testFormatDistanceInches() {
        val formatted = MeasurementMath.formatDistance(0.254f, MeasurementUnit.INCHES) // 10 inches
        assertEquals("10.0 in", formatted)
    }

    @Test
    fun testSnapToAxisVertical() {
        val origin = Vector3(1f, 0f, 1f)
        // Candidate slightly off vertical by 1.5 cm horizontally
        val candidate = Vector3(1.015f, 2.0f, 1.0f)
        val snapped = MeasurementMath.snapToAxisIfClose(origin, candidate, distanceThresholdMeters = 0.03f)

        assertEquals(origin.x, snapped.x, 1e-4f)
        assertEquals(candidate.y, snapped.y, 1e-4f)
        assertEquals(origin.z, snapped.z, 1e-4f)
    }

    @Test
    fun testSnapToExistingPoint() {
        val p1 = Vector3(0f, 0f, 0f)
        val p2 = Vector3(1f, 1f, 1f)
        val candidate = Vector3(0.01f, 0.02f, 0.01f) // very close to p1

        val snapped = MeasurementMath.snapToExistingPoint(candidate, listOf(p1, p2), snapRadiusMeters = 0.05f)
        assertNotNull(snapped)
        assertEquals(p1, snapped)

        // Candidate far away
        val farCandidate = Vector3(5f, 5f, 5f)
        val notSnapped = MeasurementMath.snapToExistingPoint(farCandidate, listOf(p1, p2), snapRadiusMeters = 0.05f)
        assertNull(notSnapped)
    }
}
