package org.remmeasure.ui.screens

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
fun CarpetScreen(viewModel: MeasureViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cozy_saved_rooms", android.content.Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(runCatching { JSONArray(prefs.getString("rooms", "[]")) }.getOrDefault(JSONArray())) }
    var roomName by remember { mutableStateOf("Living room") }
    var length by remember { mutableStateOf("") }; var width by remember { mutableStateOf("") }
    var rolls by remember { mutableStateOf("both") }; var allowance by remember { mutableStateOf(true) }
    var confirmed by remember { mutableStateOf(false) }; var result by remember { mutableStateOf<CarpetCut?>(null) }
    var error by remember { mutableStateOf("") }
    fun number(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom=120.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Cozy carpet size", style=MaterialTheme.typography.headlineMedium)
        Text("1. In Measure, mark each end of the longest room length. Repeat for the widest width. 2. Choose those saved measurements below. 3. Check, save and shop.")
        Text("For a rectangular room. Stairs, L-shapes and connected rooms need a cutting plan.")
        OutlinedTextField(value=roomName,onValueChange={roomName=it.take(60)},label={Text("Room name")},modifier=Modifier.fillMaxWidth())
        for (i in 0 until saved.length()) {
            val room = saved.getJSONObject(i)
            TextButton(onClick={length=room.getString("length");width=room.getString("width");roomName=room.getString("name");confirmed=false;result=null}) { Text("Load ${room.getString("name")}: ${room.getString("length")}m × ${room.getString("width")}m") }
        }
        Text("Measure the longest length and widest width, including door recesses. Select the correct saved measurements below, or type metres.")
        state.measurements.sortedByDescending { it.timestamp }.take(12).forEach { measurement ->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val value = String.format(Locale.UK, "%.3f", measurement.distanceMeters)
                Text("${value}m", Modifier.weight(1f))
                TextButton(onClick={ length=value; confirmed=false; result=null }) { Text("Use as length") }
                TextButton(onClick={ width=value; confirmed=false; result=null }) { Text("Use as width") }
            }
        }
        OutlinedTextField(value=length,onValueChange={length=it; confirmed=false; result=null},label={Text("Length (metres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=width,onValueChange={width=it; confirmed=false; result=null},label={Text("Width (metres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
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
            if (!confirmed || !l.isFinite() || !w.isFinite() || l<=0 || w<=0) error="Enter positive dimensions and confirm them."
            else {
                result=carpetCut(l,w,if(rolls=="both") listOf(4.0,5.0) else listOf(rolls.toDouble()),allowance)
                error=if(result==null) "A join or cutting plan is needed. Ask Cozy before ordering." else ""
            }
        }) { Text("Calculate carpet size") }
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
        result?.let { r ->
            Text(String.format(Locale.UK,"Suggested cut: %.0fm × %.1fm",r.width,r.length),style=MaterialTheme.typography.headlineSmall)
            Text(String.format(Locale.UK,"Room: %.2fm² · Purchased carpet: %.2fm²",number(length)*number(width),r.area))
            Text("Orientation may be rotated. Check product minimum length, pile direction, patterns and available widths. Phone estimates need checking before ordering. Stairs and connected rooms need a separate plan.")
            Button(onClick={
                val room=JSONObject().put("name",roomName.ifBlank { "My room" }).put("length",length).put("width",width)
                val next=JSONArray(); next.put(room)
                for(i in 0 until minOf(saved.length(),19)) if(saved.getJSONObject(i).getString("name")!=roomName) next.put(saved.getJSONObject(i))
                prefs.edit().putString("rooms",next.toString()).apply(); saved=next
            }) { Text("Save this room") }
            Button(onClick={
                val url=Uri.parse("https://cozyflooringstore.co.uk/collections/all").buildUpon().appendQueryParameter("cozy_room",roomName).appendQueryParameter("cozy_length",number(length).toString()).appendQueryParameter("cozy_width",number(width).toString()).build()
                context.startActivity(Intent(Intent.ACTION_VIEW,url))
            }) { Text("Shop with these measurements") }
            TextButton(onClick={
                val summary=String.format(Locale.UK,"Cozy estimate: room %.3fm × %.3fm; cut %.0fm × %.1fm (%.2fm²). Check before ordering.",number(length),number(width),r.width,r.length,r.area)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,summary) },"Share estimate"))
            }) { Text("Share estimate") }
        }
    }
}
