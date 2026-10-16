package org.remmeasure.ui.screens

import org.remmeasure.core.math.RoomGeometry
import org.remmeasure.core.math.FloorPoint
import org.json.JSONArray
import org.json.JSONObject
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.remmeasure.ui.viewmodel.MeasureViewModel
import kotlin.math.ceil
import kotlin.math.hypot
import java.util.Locale

data class CarpetCut(val width: Double, val length: Double) { val area get() = width * length }
fun carpetCut(length: Double, width: Double, rolls: List<Double>, allowance: Boolean): CarpetCut? {
    if (!length.isFinite() || !width.isFinite() || length <= 0 || width <= 0) return null
    val l = length + if (allowance) 0.1 else 0.0
    val w = width + if (allowance) 0.1 else 0.0
    val options = mutableListOf<CarpetCut>()
    rolls.forEach { r ->
        if (w <= r + 1e-9) options += CarpetCut(r, ceil((l-1e-9)*10)/10)
        if (l <= r + 1e-9) options += CarpetCut(r, ceil((w-1e-9)*10)/10)
    }
    return options.sortedWith(compareBy<CarpetCut> { it.area }.thenBy { it.width }).firstOrNull()
}

@Composable
fun CarpetScreen(viewModel: MeasureViewModel, importScan: Boolean = true) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cozy_saved_rooms", android.content.Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(runCatching { JSONArray(prefs.getString("rooms", "[]")) }.getOrDefault(JSONArray())) }
    var roomName by remember { mutableStateOf(state.roomName) }
    var plan by remember { mutableStateOf<List<FloorPoint>>(emptyList()) }
    var measuredArea by remember { mutableStateOf<Double?>(null) }
    var length by remember { mutableStateOf("") }; var width by remember { mutableStateOf("") }
    var rolls by remember { mutableStateOf("both") }; var allowance by remember { mutableStateOf(true) }
    var confirmed by remember { mutableStateOf(false) }; var result by remember { mutableStateOf<CarpetCut?>(null) }
    var error by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }
    LaunchedEffect(state.roomClosed, state.roomCorners) {
        if(importScan && state.roomClosed) RoomGeometry.summarize(state.roomCorners)?.let { room ->
            length=String.format(Locale.UK,"%.3f",room.length);width=String.format(Locale.UK,"%.3f",room.width)
            plan=room.points;measuredArea=room.area;confirmed=false;result=null
        }
    }
    fun number(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom=120.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Your room plan", style=MaterialTheme.typography.headlineMedium)


        OutlinedTextField(value=roomName,onValueChange={roomName=it.take(60)},label={Text("Room name")},modifier=Modifier.fillMaxWidth())
        for (i in 0 until saved.length()) {
            val room = saved.getJSONObject(i)
            TextButton(onClick={length=room.getString("length");width=room.getString("width");roomName=room.getString("name");confirmed=false;result=null;measuredArea=if(room.has("area")&&!room.isNull("area"))room.getDouble("area") else null;plan=runCatching{val a=room.getJSONArray("plan");(0 until a.length()).map{val p=a.getJSONObject(it);FloorPoint(p.getDouble("x"),p.getDouble("y"))}}.getOrDefault(emptyList())}) { Text("Load ${room.getString("name")}: ${room.getString("length")}m × ${room.getString("width")}m") }
        }
        Text("Check the plan, or enter confirmed tape measurements in metres.")
        if(importScan && viewModel.ceilingMode) Text("Ceiling-based estimate: check that this outline matches the floor, including recesses hidden below.",color=MaterialTheme.colorScheme.primary)
        if(plan.isNotEmpty()) {
            RoomPlan(plan)
            Text(String.format(Locale.UK,"Measured floor area: %.2fm²",measuredArea ?: 0.0))
            for(i in plan.indices){
                val a=plan[i];val b=plan[(i+1)%plan.size]
                val first=if(i<26)('A'.code+i).toChar().toString() else (i+1).toString()
                val j=(i+1)%plan.size;val second=if(j<26)('A'.code+j).toChar().toString() else (j+1).toString()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Wall $first–$second");Text(String.format(Locale.UK,"%.2fm",hypot(b.x-a.x,b.y-a.y)))}
            }
            Text("Check the wall sizes against a tape. Carpet dimensions below enclose the full outline.")
        } else Text("No completed room scan loaded. Finish a scan or enter tape measurements below.")
        OutlinedTextField(value=length,onValueChange={length=it; confirmed=false; result=null;plan=emptyList();measuredArea=null},label={Text("Length (metres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=width,onValueChange={width=it; confirmed=false; result=null;plan=emptyList();measuredArea=null},label={Text("Width (metres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Widths available for your chosen carpet")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("both" to "4m & 5m", "4" to "4m only", "5" to "5m only").forEach { (value,label) ->
                FilterChip(selected=rolls==value,onClick={rolls=value; result=null},label={Text(label)})
            }
        }
        Row { Checkbox(checked=allowance,onCheckedChange={allowance=it;result=null}); Text("Add 10cm to each room dimension") }
        Text("Untick if allowance is already included. Cut length rounds up to 10cm.")
        Row { Checkbox(checked=confirmed,onCheckedChange={confirmed=it; result=null}); Text("I have checked these dimensions") }
        Button(onClick={
            result=null
            val l=number(length); val w=number(width)
            if (!l.isFinite() || !w.isFinite() || l<=0 || w<=0) error="Enter a valid length and width above zero."
            else if (!confirmed) error="Confirm your dimensions to continue."
            else {
                result=carpetCut(l,w,if(rolls=="both") listOf(4.0,5.0) else listOf(rolls.toDouble()),allowance)
                error=if(result==null) "A join or cutting plan is needed. Ask Cozy before ordering." else ""
            }
        }) { Text("Calculate carpet size") }
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        if(saveMessage.isNotEmpty())Text(saveMessage,color=MaterialTheme.colorScheme.primary)
        if(confirmed && number(length)>0 && number(width)>0) {
            Button(onClick={
                val outline=JSONArray();plan.forEach{outline.put(JSONObject().put("x",it.x).put("y",it.y))}
                val room=JSONObject().put("name",roomName.ifBlank { "My room" }).put("length",length).put("width",width).put("area",measuredArea).put("plan",outline)
                val next=JSONArray(); next.put(room)
                for(i in 0 until minOf(saved.length(),19)) if(saved.getJSONObject(i).getString("name")!=roomName) next.put(saved.getJSONObject(i))
                prefs.edit().putString("rooms",next.toString()).apply(); saved=next;saveMessage="Room saved on this phone."
            }) { Text("Save this room") }
        }
        result?.let { r ->
            Text(String.format(Locale.UK,"Suggested cut: %.0fm × %.1fm",r.width,r.length),style=MaterialTheme.typography.headlineSmall)
            Text(String.format(Locale.UK,"Room: %.2fm² · Purchased carpet: %.2fm²",measuredArea ?: (number(length)*number(width)),r.area))
            Text("Orientation may be rotated. Check product minimum length, pile direction, patterns and available widths. Phone estimates need checking before ordering. Stairs and connected rooms need a separate plan.")
            Button(onClick={
                val outline=JSONArray();plan.forEach{outline.put(JSONObject().put("x",it.x).put("y",it.y))}
                val url=Uri.parse("https://cozyflooringstore.co.uk/collections/all").buildUpon().appendQueryParameter("cozy_allowance",if(allowance) "1" else "0").appendQueryParameter("cozy_plan",outline.toString()).appendQueryParameter("cozy_room",roomName).appendQueryParameter("cozy_length",number(length).toString()).appendQueryParameter("cozy_width",number(width).toString()).build()
                context.startActivity(Intent(Intent.ACTION_VIEW,url))
            }) { Text("Shop with these measurements") }
            TextButton(onClick={
                val summary=String.format(Locale.UK,"Cozy estimate: room %.3fm × %.3fm; cut %.0fm × %.1fm (%.2fm²). Check before ordering.",number(length),number(width),r.width,r.length,r.area)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,summary) },"Share estimate"))
            }) { Text("Share estimate") }
        }
    }
}
