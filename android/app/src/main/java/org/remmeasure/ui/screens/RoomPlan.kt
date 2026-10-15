package org.remmeasure.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import org.remmeasure.core.math.FloorPoint
import kotlin.math.hypot
import java.util.Locale

@Composable
fun RoomPlan(points: List<FloorPoint>) {
    if(points.size<3)return
    Canvas(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFF5F1EB))) {
        val l=points.maxOf{it.x}.coerceAtLeast(0.1);val w=points.maxOf{it.y}.coerceAtLeast(0.1)
        val scale=minOf((size.width-100)/l,(size.height-100)/w).toFloat()
        val ox=(size.width-l.toFloat()*scale)/2;val oy=(size.height-w.toFloat()*scale)/2
        val path=Path();path.moveTo(ox+points[0].x.toFloat()*scale,oy+points[0].y.toFloat()*scale)
        points.drop(1).forEach{path.lineTo(ox+it.x.toFloat()*scale,oy+it.y.toFloat()*scale)};path.close()
        drawPath(path,Color(0x338B7566));drawPath(path,Color(0xFF8B7566),style=Stroke(width=4f))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(48,58,52);textSize=28f;textAlign=Paint.Align.CENTER}
        for(i in points.indices){val a=points[i];val b=points[(i+1)%points.size]
            drawCircle(Color(0xFF303A34),6f,androidx.compose.ui.geometry.Offset(ox+a.x.toFloat()*scale,oy+a.y.toFloat()*scale))
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.UK,"%.2fm",hypot(b.x-a.x,b.y-a.y)),ox+((a.x+b.x)/2).toFloat()*scale,oy+((a.y+b.y)/2).toFloat()*scale-12,paint)
        }
    }
}
