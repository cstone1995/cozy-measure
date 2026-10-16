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
    val roomName: String = "Living room",
    val roomCorners: List<Vector3> = emptyList(),
    val roomClosed: Boolean = false,
    val roomMessage: String = "Move your phone slowly over the floor to find it.",
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

    var ceilingMode: Boolean = false
        private set
    val surfaceName: String get() = if (ceilingMode) "ceiling" else "floor"
    fun setCeilingMode(enabled: Boolean) { ceilingMode = enabled }

    fun onSurfaceHit(rawHitPoint: Vector3, isVerticalSnap: Boolean = false, isStable: Boolean = true) {
        val state = _uiState.value
        if(state.roomClosed) return
        val floor = state.roomCorners.firstOrNull()?.y ?: rawHitPoint.y
        if(kotlin.math.abs(rawHitPoint.y-floor)>0.08f){ invalidateRoomTarget("Aim at the same $surfaceName level. Do not mix floor and ceiling corners.");return }
        val point=Vector3(rawHitPoint.x,floor,rawHitPoint.z)
        _uiState.update { it.copy(candidatePoint=point,roomMessage=if(isStable) "Aim at the $surfaceName corner and tap Add corner." else "Surface found. Steady the phone for a better reading, then tap Add corner.",liveDistanceMeters=state.roomCorners.lastOrNull()?.distanceTo(point) ?: 0f,trackingStatus=TrackingStatus.TRACKING_READY) }
    }

    fun updateTrackedCorners(points: List<Vector3>) {
        val state=_uiState.value
        if(state.roomClosed||points.size!=state.roomCorners.size||points.isEmpty())return
        val floor=points.first().y
        val flat=points.map{Vector3(it.x,floor,it.z)}
        _uiState.update{it.copy(roomCorners=flat,currentStartPoint=flat.last(),measurements=flat.zipWithNext{a,b->MeasurementSegment(start=a,end=b)})}
    }

    fun setRoomName(name: String) { _uiState.update { it.copy(roomName=name.take(60)) } }

    fun invalidateRoomTarget(message: String) { _uiState.update { it.copy(candidatePoint=null,roomMessage=message) } }
    fun closeRoom(): Boolean {
        val state=_uiState.value
        if(org.remmeasure.core.math.RoomGeometry.summarize(state.roomCorners)==null){
            _uiState.update{it.copy(roomMessage="Outline overlaps or is too small. Undo a corner and try again.")};return false
        }
        val edges=state.roomCorners.indices.map{ i -> MeasurementSegment(start=state.roomCorners[i],end=state.roomCorners[(i+1)%state.roomCorners.size]) }
        _uiState.update{it.copy(roomClosed=true,currentStartPoint=null,candidatePoint=null,measurements=edges,roomMessage="Room complete. Open Carpet size to review the floor plan.")};return true
    }
    fun undoRoomCorner() {
        val state=_uiState.value
        val points=if(state.roomClosed)state.roomCorners else state.roomCorners.dropLast(1)
        _uiState.update{it.copy(roomCorners=points,roomClosed=false,currentStartPoint=points.lastOrNull(),measurements=points.zipWithNext{a,b->MeasurementSegment(start=a,end=b)},candidatePoint=null,roomMessage="Aim at the next $surfaceName corner and add it.")}
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
        val state=_uiState.value
        if(state.roomClosed)return false
        val candidate=state.candidatePoint ?: return false
        if(state.roomCorners.size>=40){_uiState.update{it.copy(roomMessage="Maximum 40 corners. Finish this room.")};return false}
        if(state.roomCorners.lastOrNull()?.distanceTo(candidate)?.let{it<0.1f}==true){_uiState.update{it.copy(roomMessage="Move to the next corner; these points are too close.")};return false}
        val points=state.roomCorners+candidate
        _uiState.update{it.copy(roomCorners=points,currentStartPoint=candidate,candidatePoint=null,measurements=points.zipWithNext{a,b->MeasurementSegment(start=a,end=b)},roomMessage="Corner ${points.size} saved. Follow the wall to the next corner.")}
        return true
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
                roomCorners = emptyList(),
                candidatePoint = null,
                roomClosed = false,
                roomMessage = "Find the $surfaceName, then add each corner in order.",
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
