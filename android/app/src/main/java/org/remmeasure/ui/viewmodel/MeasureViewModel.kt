package org.remmeasure.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.remmeasure.core.math.MeasurementMath
import org.remmeasure.core.model.AppThemeMode
import org.remmeasure.core.model.MeasurementSegment
import org.remmeasure.core.model.MeasurementUnit
import org.remmeasure.core.model.TrackingStatus
import org.remmeasure.core.model.Vector3

data class MeasureUiState(
    val measurements: List<MeasurementSegment> = emptyList(),
    val currentStartPoint: Vector3? = null,
    val candidatePoint: Vector3? = null,
    val liveDistanceMeters: Float = 0f,
    val isSnappedToAxis: Boolean = false,
    val isSnappedToPoint: Boolean = false,
    val selectedUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val trackingStatus: TrackingStatus = TrackingStatus.SEARCHING_FOR_SURFACES,
    val selectedSegment: MeasurementSegment? = null,
    val isVibrationEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isDynamicColorEnabled: Boolean = true
)

class MeasureViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("remmeasure_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MeasureUiState(
            selectedUnit = runCatching {
                MeasurementUnit.valueOf(prefs.getString("unit", MeasurementUnit.METRIC.name) ?: MeasurementUnit.METRIC.name)
            }.getOrDefault(MeasurementUnit.METRIC),
            isVibrationEnabled = prefs.getBoolean("vibration", true),
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
            isDynamicColorEnabled = prefs.getBoolean("dynamic_color", true)
        )
    )
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    fun onSurfaceHit(rawHitPoint: Vector3, isVerticalSnap: Boolean = false) {
        val state = _uiState.value
        val existingEndpoints = state.measurements.flatMap { listOf(it.start, it.end) }

        // Check if candidate point snaps to any existing placed point
        val snappedToPoint = if (!isVerticalSnap) {
            MeasurementMath.snapToExistingPoint(rawHitPoint, existingEndpoints)
        } else null

        val resolvedPoint = if (snappedToPoint != null) {
            snappedToPoint
        } else if (isVerticalSnap) {
            rawHitPoint
        } else if (state.currentStartPoint != null) {
            // Check axis-locking (horizontal or vertical) from start point
            MeasurementMath.snapToAxisIfClose(state.currentStartPoint, rawHitPoint)
        } else {
            rawHitPoint
        }

        val isSnappedPoint = snappedToPoint != null
        val isSnappedAxis = isVerticalSnap || (state.currentStartPoint != null && resolvedPoint != rawHitPoint && !isSnappedPoint)

        val liveDistance = if (state.currentStartPoint != null) {
            state.currentStartPoint.distanceTo(resolvedPoint)
        } else 0f

        _uiState.update {
            it.copy(
                candidatePoint = resolvedPoint,
                liveDistanceMeters = liveDistance,
                isSnappedToAxis = isSnappedAxis,
                isSnappedToPoint = isSnappedPoint,
                trackingStatus = if (it.currentStartPoint != null) TrackingStatus.POINT_PLACED else TrackingStatus.TRACKING_READY
            )
        }
    }

    fun onTrackingStateUpdated(newStatus: TrackingStatus) {
        _uiState.update { current ->
            // Only overwrite if not actively drawing a point
            if (current.currentStartPoint != null && newStatus == TrackingStatus.TRACKING_READY) {
                current.copy(trackingStatus = TrackingStatus.POINT_PLACED)
            } else {
                current.copy(trackingStatus = newStatus)
            }
        }
    }

    fun onPrimaryActionButtonClicked(): Boolean {
        val state = _uiState.value
        val candidate = state.candidatePoint ?: return false

        return if (state.currentStartPoint == null) {
            // Start a new measurement segment
            _uiState.update {
                it.copy(
                    currentStartPoint = candidate,
                    trackingStatus = TrackingStatus.POINT_PLACED
                )
            }
            true
        } else {
            // Finish current segment
            val newSegment = MeasurementSegment(
                start = state.currentStartPoint,
                end = candidate
            )
            _uiState.update {
                it.copy(
                    measurements = it.measurements + newSegment,
                    currentStartPoint = null,
                    liveDistanceMeters = 0f,
                    trackingStatus = TrackingStatus.TRACKING_READY,
                    selectedSegment = null
                )
            }
            true
        }
    }

    fun cancelActiveMeasurement() {
        _uiState.update {
            it.copy(
                currentStartPoint = null,
                liveDistanceMeters = 0f,
                trackingStatus = TrackingStatus.TRACKING_READY
            )
        }
    }

    fun clearAllMeasurements() {
        _uiState.update {
            it.copy(
                measurements = emptyList(),
                currentStartPoint = null,
                liveDistanceMeters = 0f,
                selectedSegment = null,
                trackingStatus = TrackingStatus.TRACKING_READY
            )
        }
    }

    fun deleteSegment(id: String) {
        _uiState.update { current ->
            val updated = current.measurements.filterNot { it.id == id }
            current.copy(
                measurements = updated,
                selectedSegment = if (current.selectedSegment?.id == id) null else current.selectedSegment
            )
        }
    }

    fun selectSegment(segment: MeasurementSegment?) {
        _uiState.update { it.copy(selectedSegment = segment) }
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        prefs.edit().putString("unit", unit.name).apply()
        _uiState.update { it.copy(selectedUnit = unit) }
    }

    fun toggleVibrations() {
        val nextValue = !_uiState.value.isVibrationEnabled
        prefs.edit().putBoolean("vibration", nextValue).apply()
        _uiState.update { it.copy(isVibrationEnabled = nextValue) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _uiState.update { it.copy(isDynamicColorEnabled = enabled) }
    }
}
