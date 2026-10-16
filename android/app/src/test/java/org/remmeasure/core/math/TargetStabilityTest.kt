package org.remmeasure.core.math
import org.junit.Assert.*
import org.junit.Test
import org.remmeasure.core.model.Vector3
class TargetStabilityTest {
 @Test fun holdsStillBeforeAccepting(){val s=TargetStability();for(i in 1L..7L)assertFalse(s.update(Vector3(1f,0f,2f),i));assertTrue(s.update(Vector3(1.005f,0f,2f),8L))}
 @Test fun movingTargetResetsWindow(){val s=TargetStability();for(i in 1L..8L)assertFalse(s.update(Vector3(i.toFloat(),0f,0f),i))}
 @Test fun duplicateFramesAndLostTrackingDoNotQualify(){val s=TargetStability();for(i in 1..10)assertFalse(s.update(Vector3.Zero,1L));s.reset();assertFalse(s.update(Vector3.Zero,2L))}
}
