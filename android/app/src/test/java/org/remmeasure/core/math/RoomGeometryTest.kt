package org.remmeasure.core.math
import org.junit.Assert.*
import org.junit.Test
import org.remmeasure.core.model.Vector3
class RoomGeometryTest {
 private fun p(x:Float,z:Float)=Vector3(x,0f,z)
 @Test fun rectangle(){val r=RoomGeometry.summarize(listOf(p(0f,0f),p(4f,0f),p(4f,3f),p(0f,3f)))!!;assertEquals(12.0,r.area,0.0001);assertEquals(14.0,r.perimeter,0.0001);assertEquals(12.0,r.length*r.width,0.0001)}
 @Test fun concaveRoom(){val r=RoomGeometry.summarize(listOf(p(0f,0f),p(4f,0f),p(4f,2f),p(2f,2f),p(2f,4f),p(0f,4f)))!!;assertEquals(12.0,r.area,0.0001);assertEquals(16.0,r.length*r.width,0.0001)}
 @Test fun crossingOutlineRejected(){assertNull(RoomGeometry.summarize(listOf(p(0f,0f),p(4f,3f),p(4f,0f),p(0f,3f))))}
 @Test fun insufficientAndRepeatedCornersRejected(){assertNull(RoomGeometry.summarize(listOf(p(0f,0f),p(4f,0f))));assertNull(RoomGeometry.summarize(listOf(p(0f,0f),p(4f,0f),p(4f,0f),p(0f,3f))))}
 @Test fun rotationDoesNotChangeArea(){val r=RoomGeometry.summarize(listOf(p(0f,0f),p(3f,3f),p(1f,5f),p(-2f,2f)))!!;assertEquals(12.0,r.area,0.0001);assertEquals(12.0,r.length*r.width,0.0001)}
}
