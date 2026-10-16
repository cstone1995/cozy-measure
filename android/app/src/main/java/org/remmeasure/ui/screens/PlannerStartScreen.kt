package org.remmeasure.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.remmeasure.R
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
fun PlannerStartScreen(onScan:(String, Boolean)->Unit,onRooms:()->Unit,onManual:()->Unit) {
    val context=LocalContext.current
    var roomName by rememberSaveable { mutableStateOf("Living room") }
    var ceiling by rememberSaveable { mutableStateOf(false) }
    val surface=if(ceiling) "ceiling" else "floor"
    var guide by rememberSaveable { mutableStateOf(-1) }
    val rooms=runCatching{JSONArray(context.getSharedPreferences("cozy_saved_rooms",0).getString("rooms","[]")).length()}.getOrDefault(0)
    val titles=listOf("First, find the $surface","Follow the room’s corners","Review, save and shop")
    val instructions=listOf(
        if(ceiling) "Turn on the lights. Scan a broad ceiling patch near a light fitting or textured edge. Move slowly sideways to help tracking. Once a surface is detected, aim at the wall–ceiling corners. If no ceiling is found, use Floor corners." else "Turn on the lights. Point your camera at a clear patch of floor and move slowly side to side. Wait for Surface found before adding a corner.",
        "Aim where the $surface meets the wall at your first corner. Tap Add corner. Walk around the room in one direction and add every corner, including alcoves and door recesses. Do not add the first corner twice.",
        "After the last corner, tap Finish room. Check the outline and wall lengths. Name and save the room, then choose a carpet cut. Phone estimates need checking before you order."
    )
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F1EB)).statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Column { Text("cozy",fontFamily=FontFamily.Serif,fontSize=38.sp,color=Color(0xFF6B5547)); Text("FLOORING · ROOM MEASURING",fontSize=10.sp,letterSpacing=1.4.sp,color=Color(0xFF6B5547)) }
            Text("Made for your home",style=MaterialTheme.typography.labelSmall,color=Color(0xFF6B5547))
        }
        if(guide<0) {
            Image(painter=painterResource(R.drawable.cozy_home),contentDescription="Warm, neutral interior from the Cozy Flooring storefront",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)))
            Text("A cozier home\nstarts here.",style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Serif,color=Color(0xFF303A34))
            Text("Measure your space. Save your room. Find your flooring.",color=Color(0xFF66645C))
            Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFE8DDD4)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("01  Measure",style=MaterialTheme.typography.labelMedium)
                    Text("02  Save",style=MaterialTheme.typography.labelMedium)
                    Text("03  Shop",style=MaterialTheme.typography.labelMedium)
                }
            }
            OutlinedTextField(value=roomName,onValueChange={roomName=it.take(60)},label={Text("Which room are you measuring?")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Choose your scan",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilterChip(selected=!ceiling,onClick={ceiling=false},label={Text("Floor corners")})
                FilterChip(selected=ceiling,onClick={ceiling=true},label={Text("Ceiling corners")})
            }
            if(ceiling) Text("Use a flat ceiling only where its outline matches the floor. Include floor-only recesses separately. Sloped ceilings, beams and different ceiling heights need floor measurements.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={guide=0},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("Measure a room →")}
            OutlinedButton(onClick=onRooms,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("My saved rooms ($rooms)")}
            TextButton(onClick=onManual,modifier=Modifier.fillMaxWidth()){Text("I already have tape measurements")}
            HorizontalDivider()
            Text("Your home, your measurements.\nNo account needed. Saved rooms stay on this phone.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://cozyflooringstore.co.uk/")))}){Text("Browse Cozy flooring ↗")}
        } else {
            TextButton(onClick={if(guide>0)guide-- else guide= -1}){Text("← Back")}
            Text("HOW TO MEASURE · ${guide+1} OF 3",color=Color(0xFF8B7566),style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={(guide+1)/3f},modifier=Modifier.fillMaxWidth())
            PlannerIllustration(guide)
            Text(titles[guide],style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text(instructions[guide],style=MaterialTheme.typography.bodyLarge)
            Button(onClick={if(guide<2)guide++ else onScan(roomName.ifBlank{"My room"},ceiling)},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(guide<2)"Next →" else "Open camera & start →")}
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
