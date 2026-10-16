package org.remmeasure.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun PlannerStartScreen(onScan:(String)->Unit,onRooms:()->Unit,onManual:()->Unit) {
    val context=LocalContext.current
    var roomName by rememberSaveable { mutableStateOf("Living room") }
    var guide by rememberSaveable { mutableStateOf(-1) }
    val rooms=runCatching{JSONArray(context.getSharedPreferences("cozy_saved_rooms",0).getString("rooms","[]")).length()}.getOrDefault(0)
    val titles=listOf("First, find the floor","Follow the room’s corners","Review, save and shop")
    val instructions=listOf(
        "Turn on the lights. Point your camera down at a clear patch of floor and move slowly side to side. Wait for Floor found before adding a corner.",
        "Aim where the floor meets the wall at your first corner. Tap Add corner. Walk around the room in one direction and add every corner, including alcoves and door recesses. Do not add the first corner twice.",
        "After the last corner, tap Finish room. Check the outline and wall lengths. Name and save the room, then choose a carpet cut. Phone estimates need checking before you order."
    )
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F1EB)).statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("COZY FLOORING",color=Color(0xFF8B7566),style=MaterialTheme.typography.labelLarge)
        if(guide<0) {
            Text("Your room.\nA clearer plan.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text("Measure your room with your camera, save its floor plan and find your carpet size.")
            PlannerIllustration(2)
            OutlinedTextField(value=roomName,onValueChange={roomName=it.take(60)},label={Text("Which room are you measuring?")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(onClick={guide=0},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("Measure a room →")}
            OutlinedButton(onClick=onRooms,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("My saved rooms ($rooms)")}
            TextButton(onClick=onManual,modifier=Modifier.fillMaxWidth()){Text("I already have tape measurements")}
            HorizontalDivider()
            Text("No account needed. Your rooms stay on this phone.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://cozyflooringstore.co.uk/")))}){Text("Visit Cozy Flooring ↗")}
        } else {
            TextButton(onClick={if(guide>0)guide-- else guide= -1}){Text("← Back")}
            Text("HOW TO MEASURE · ${guide+1} OF 3",color=Color(0xFF8B7566),style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={(guide+1)/3f},modifier=Modifier.fillMaxWidth())
            PlannerIllustration(guide)
            Text(titles[guide],style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text(instructions[guide],style=MaterialTheme.typography.bodyLarge)
            Button(onClick={if(guide<2)guide++ else onScan(roomName.ifBlank{"My room"})},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(guide<2)"Next →" else "Open camera & start →")}
            Text("Use the camera only where you can walk safely. Stairs and rooms on different levels need a separate plan.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlannerIllustration(step:Int) {
    Canvas(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFFE8DDD4))) {
        val w=size.width;val h=size.height;val ink=Color(0xFF8B7566)
        val p=listOf(Offset(w*.17f,h*.22f),Offset(w*.80f,h*.22f),Offset(w*.80f,h*.70f),Offset(w*.55f,h*.70f),Offset(w*.55f,h*.84f),Offset(w*.17f,h*.84f))
        val path=Path().apply{moveTo(p[0].x,p[0].y);p.drop(1).forEach{lineTo(it.x,it.y)};close()}
        drawPath(path,Color(0x22FFFFFF));drawPath(path,ink,style=Stroke(5f))
        if(step>0)p.forEach{drawCircle(ink,9f,it)}
        if(step==0){
            drawRoundRect(Color(0xFF303A34),Offset(w*.39f,h*.18f),Size(w*.22f,h*.66f),androidx.compose.ui.geometry.CornerRadius(18f))
            drawRoundRect(Color(0xFFF5F1EB),Offset(w*.41f,h*.23f),Size(w*.18f,h*.54f),androidx.compose.ui.geometry.CornerRadius(10f))
            drawLine(ink,Offset(w*.46f,h*.49f),Offset(w*.54f,h*.49f),4f);drawLine(ink,Offset(w*.50f,h*.43f),Offset(w*.50f,h*.55f),4f)
        }
        if(step==2){drawLine(Color(0xFF303A34),Offset(w*.36f,h*.46f),Offset(w*.46f,h*.57f),9f);drawLine(Color(0xFF303A34),Offset(w*.46f,h*.57f),Offset(w*.65f,h*.36f),9f)}
    }
}
