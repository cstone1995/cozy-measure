package org.remmeasure.core.math
import org.remmeasure.core.model.Vector3

class TargetStability(private val required: Int = 8, private val radius: Float = 0.02f) {
    private var origin: Vector3? = null
    private var count = 0
    private var lastTimestamp = -1L
    fun reset(){origin=null;count=0;lastTimestamp= -1L}
    fun update(point: Vector3, timestamp: Long): Boolean {
        if(!point.x.isFinite()||!point.y.isFinite()||!point.z.isFinite()){reset();return false}
        if(timestamp<=lastTimestamp)return false
        lastTimestamp=timestamp
        if(origin==null||origin!!.distanceTo(point)>radius){origin=point;count=1}else count++
        return count>=required
    }
}
