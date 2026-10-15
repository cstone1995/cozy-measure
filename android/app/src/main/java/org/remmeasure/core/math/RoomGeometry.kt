package org.remmeasure.core.math

import org.remmeasure.core.model.Vector3
import kotlin.math.*

data class FloorPoint(val x: Double, val y: Double)
data class RoomOutline(val length: Double, val width: Double, val area: Double, val perimeter: Double, val points: List<FloorPoint>)

object RoomGeometry {
    private fun cross(a: FloorPoint,b: FloorPoint,c: FloorPoint)=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
    private fun on(a: FloorPoint,b: FloorPoint,p: FloorPoint)=abs(cross(a,b,p))<1e-7 && p.x>=min(a.x,b.x)-1e-7 && p.x<=max(a.x,b.x)+1e-7 && p.y>=min(a.y,b.y)-1e-7 && p.y<=max(a.y,b.y)+1e-7
    private fun intersects(a: FloorPoint,b: FloorPoint,c: FloorPoint,d: FloorPoint): Boolean {
        val x=cross(a,b,c);val y=cross(a,b,d);val z=cross(c,d,a);val w=cross(c,d,b)
        return (x*y < -1e-12 && z*w < -1e-12)||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b)
    }
    fun summarize(corners: List<Vector3>): RoomOutline? {
        if(corners.size<3||corners.size>40||corners.any{!it.x.isFinite()||!it.y.isFinite()||!it.z.isFinite()})return null
        val p=corners.map{FloorPoint(it.x.toDouble(),it.z.toDouble())};val n=p.size
        var area=0.0;var perimeter=0.0
        for(i in p.indices){
            val a=p[i];val b=p[(i+1)%n];val edge=hypot(b.x-a.x,b.y-a.y)
            if(edge<0.1)return null
            perimeter+=edge;area+=a.x*b.y-b.x*a.y
            for(j in i+1 until n){
                if(j==i+1 || (i==0&&j==n-1))continue
                if(intersects(a,b,p[j],p[(j+1)%n]))return null
            }
        }
        area=abs(area)/2
        if(area<0.05)return null
        var bestArea=Double.POSITIVE_INFINITY;var best:List<FloorPoint> = emptyList()
        var length=0.0;var width=0.0
        for(i in p.indices){
            val a=p[i];val b=p[(i+1)%n];val theta=atan2(b.y-a.y,b.x-a.x)
            val rotated=p.map{FloorPoint((it.x-a.x)*cos(theta)+(it.y-a.y)*sin(theta),-(it.x-a.x)*sin(theta)+(it.y-a.y)*cos(theta))}
            val minX=rotated.minOf{it.x};val minY=rotated.minOf{it.y};val l=rotated.maxOf{it.x}-minX;val w=rotated.maxOf{it.y}-minY
            if(l*w<bestArea){bestArea=l*w;length=l;width=w;best=rotated.map{FloorPoint(it.x-minX,it.y-minY)}}
        }
        if(length>100||width>100)return null
        return RoomOutline(length,width,area,perimeter,best)
    }
}
