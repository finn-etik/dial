/*
 * DIAL — web radio stream player in the Field Design Language.
 * Signal violet #6200EA.
 * Reason: each Field app carries a distinct signal accent; Relay owns orange.
 * EXT: INPUT ROW — BasicTextField station name/url inputs (foundation
 * primitive, styled per tokens: paper card, ink border, monospace).
 *
 * Playback: framework MediaPlayer (HTTP/ICY streams, prepareAsync).
 * Stations persist as JSON in SharedPreferences (org.json, framework only).
 * Auto-retry re-tunes 3 s after a stream error when RETRY is on.
 */

package com.field.dial

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/* ============================================================
   FIELD TOKENS — fixed core. Accent per DIAL skin section.
   ============================================================ */
val FieldBody = Color(0xFFDADAD4)
val FieldPaper = Color(0xFFF5F5F1)
val FieldInk = Color(0xFF191919)
val FieldDim = Color(0xFF8A8A82)
val FieldGrid = Color(0xFFC4C4BC)
val FieldViolet = Color(0xFF6200EA) // signal accent: live / active / urgent only

/* station: one saved web stream */
data class Station(val id: Int, val name: String, val url: String)

fun clock(s: Int): String = String.format(
    Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60,
)

/* FieldText: thin wrapper over BasicText */
@Composable
fun FieldText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    color: Color = FieldInk,
    tracking: TextUnit = 0.sp,
    align: TextAlign? = null,
    lineheight: TextUnit? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = size,
            fontWeight = weight,
            letterSpacing = tracking,
            color = color,
            textAlign = align,
            lineHeight = lineheight ?: TextUnit.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/* LED: hard on/off blink. slow = 250/750, fast = 100/400. */
@Composable
fun BlinkingLed(size: Dp = 8.dp, mode: String, color: Color = FieldViolet) {
    var on by remember(mode) { mutableStateOf(true) }
    LaunchedEffect(mode) {
        when (mode) {
            "solid" -> on = true
            "off" -> on = false
            "fast" -> while (true) { on = true; delay(100); on = false; delay(400) }
            else -> while (true) { on = true; delay(250); on = false; delay(750) }
        }
    }
    Box(
        Modifier
            .size(size)
            .background(if (on) color else color.copy(alpha = 0.15f), CircleShape)
    )
}

/* Key: paper square, 3 dp radius, no ripple. Press = border weight. */
@Composable
fun FieldKey(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    size: TextUnit = 15.sp,
    variant: String = "paper",
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accentVariant = variant == "accent"
    val inkVariant = variant == "ink"
    val bg = when {
        accentVariant -> FieldViolet
        inkVariant -> FieldInk
        else -> FieldPaper
    }
    val fg = when {
        accentVariant || inkVariant -> FieldPaper
        active -> FieldViolet
        else -> FieldInk
    }
    val borderColor = when {
        inkVariant -> FieldPaper
        active -> FieldViolet
        else -> FieldInk
    }
    val borderWidth = if (pressed || active) 2.dp else 1.5.dp
    Box(
        modifier
            .height(height)
            .background(bg, RoundedCornerShape(3.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(3.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        FieldText(label, size, weight = FontWeight.Bold, color = fg, tracking = 1.sp)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    FieldText(text, 10.sp, modifier, weight = FontWeight.Bold, color = FieldInk, tracking = 2.sp)
}

/* Toggle row: paper card, label + caption, square checkbox */
@Composable
fun FieldToggleRow(label: String, caption: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FieldPaper, RoundedCornerShape(4.dp))
            .border(1.5.dp, FieldInk, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            FieldText(label, 12.sp, color = FieldInk, tracking = 1.sp)
            FieldText(caption, 9.sp, Modifier.padding(top = 2.dp), color = FieldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(20.dp).border(1.5.dp, FieldInk)) {
            if (checked) {
                Box(Modifier.padding(4.dp).size(12.dp).background(FieldViolet))
            }
        }
    }
}

/* Dot grid texture: 26 dp pitch, computed once per size */
fun Modifier.fieldGrid(): Modifier = drawWithCache {
    val pitch = 26.dp.toPx()
    val dots = ArrayList<Offset>()
    var x = 0f
    while (x <= size.width) {
        var y = 0f
        while (y <= size.height) {
            dots.add(Offset(x, y))
            y += pitch
        }
        x += pitch
    }
    val r = 1.dp.toPx()
    onDrawBehind { for (c in dots) drawCircle(FieldGrid, radius = r, center = c) }
}

/* ============================================================
   DIAL app
   ============================================================ */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DialApp() }
    }
}

@Composable
fun DialApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("field_dial", Context.MODE_PRIVATE) }

    /* first run: seed with a few long-standing public streams */
    val seed = listOf(
        Station(1, "SOMA GROOVE", "https://ice1.somafm.com/groovesalad-128-mp3"),
        Station(2, "SOMA DRONE", "https://ice1.somafm.com/dronezone-128-mp3"),
        Station(3, "RADIO PARADISE", "https://stream.radioparadise.com/mp3-128"),
    )

    /* load once: stations + next id, seeding and persisting on first run */
    val stations = remember {
        val list = mutableStateListOf<Station>()
        var maxId = 0
        try {
            val arr = JSONArray(prefs.getString("STATIONS", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Station(o.getInt("id"), o.getString("n"), o.getString("u")))
                if (o.getInt("id") > maxId) maxId = o.getInt("id")
            }
        } catch (_: Exception) {
            list.clear()
        }
        if (list.isEmpty() && !prefs.contains("STATIONS")) {
            list.addAll(seed)
            maxId = seed.size
        }
        list
    }
    var idCounter by remember {
        mutableIntStateOf(stations.maxOfOrNull { it.id }?.plus(1) ?: 1)
    }

    var selId by remember { mutableIntStateOf(prefs.getInt("SEL", -1)) }
    var play by remember { mutableStateOf<Station?>(null) }
    var phase by remember { mutableStateOf("off") } // off | connect | on | err
    var airtime by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(prefs.getInt("TOTAL", 0)) }
    var vol by remember { mutableIntStateOf(prefs.getInt("VOL", 7)) }
    var muted by remember { mutableStateOf(false) }
    var retryOn by remember { mutableStateOf(prefs.getBoolean("RETRY", true)) }
    var attempts by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var nameIn by remember { mutableStateOf("") }
    var urlIn by remember { mutableStateOf("") }

    var mp by remember { mutableStateOf<MediaPlayer?>(null) }

    fun saveStations() {
        val arr = JSONArray()
        stations.forEach { arr.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url)) }
        prefs.edit().putString("STATIONS", arr.toString()).apply()
    }
    fun persistTotal() { prefs.edit().putInt("TOTAL", total).apply() }
    fun volumeFraction(): Float = if (muted) 0f else vol / 10f

    fun releasePlayer() {
        mp?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        mp = null
    }

    fun tune(st: Station) {
        releasePlayer()
        persistTotal()
        play = st
        selId = st.id
        prefs.edit().putInt("SEL", st.id).apply()
        phase = "connect"
        airtime = 0
        attempts = 0
        msg = ""
        val m = MediaPlayer()
        try {
            m.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            m.setDataSource(st.url)
            val v = volumeFraction()
            m.setVolume(v, v)
            m.setOnPreparedListener { mm -> mm.start(); phase = "on"; attempts = 0 }
            m.setOnErrorListener { mm, _, _ ->
                phase = "err"
                try { mm.release() } catch (_: Exception) {}
                if (mp === mm) mp = null
                true
            }
            m.prepareAsync()
            mp = m
        } catch (_: Exception) {
            m.release()
            phase = "err"
        }
    }

    fun stop() {
        releasePlayer()
        persistTotal()
        phase = "off"
        play = null
        airtime = 0
        attempts = 0
    }

    fun addStation() {
        val name = nameIn.trim().uppercase()
        val url = urlIn.trim()
        if (name.isEmpty() || url.isEmpty()) { msg = "ENTER NAME AND URL"; return }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) { msg = "URL MUST START HTTP"; return }
        stations.add(Station(idCounter++, name, url))
        saveStations()
        nameIn = ""
        urlIn = ""
        msg = "STATION SAVED"
    }

    fun delStation() {
        val st = stations.find { it.id == selId } ?: return
        if (play?.id == st.id) stop()
        stations.remove(st)
        saveStations()
        selId = -1
        prefs.edit().putInt("SEL", -1).apply()
        msg = "STATION DELETED"
    }

    /* persist volume + retry setting */
    LaunchedEffect(vol) { prefs.edit().putInt("VOL", vol).apply() }
    LaunchedEffect(retryOn) { prefs.edit().putBoolean("RETRY", retryOn).apply() }

    /* live volume changes */
    LaunchedEffect(vol, muted) {
        val v = volumeFraction()
        mp?.setVolume(v, v)
    }

    /* auto retry: re-tune 3 s after a stream error.
       keyed on retryOn too, so flipping RETRY while in ERR re-arms it. */
    LaunchedEffect(phase, retryOn) {
        if (phase == "err" && retryOn && play != null) {
            delay(3000)
            attempts += 1
            play?.let { tune(it) }
        }
    }

    /* airtime: 1 s tick while on air */
    LaunchedEffect(phase) {
        if (phase != "on") return@LaunchedEffect
        while (true) {
            delay(1000)
            airtime += 1
            total += 1
            persistTotal()
        }
    }

    /* cleanup */
    DisposableEffect(Unit) {
        onDispose {
            releasePlayer()
            persistTotal()
        }
    }

    val sel = stations.find { it.id == selId }
    val ledMode = when (phase) {
        "on" -> "slow"
        "connect" -> "fast"
        "err" -> "solid"
        else -> "off"
    }
    val statusWord = when (phase) {
        "on" -> "ON AIR"
        "connect" -> "CONNECT"
        "err" -> "ERR"
        else -> "STANDBY"
    }
    val dimLine = when (phase) {
        "on" -> "STREAM LIVE / PLAYING"
        "connect" -> if (attempts > 0) String.format(Locale.ROOT, "RETRY %02d / CONNECTING", attempts) else "CONNECTING TO STREAM"
        "err" -> if (retryOn) "STREAM DOWN / AUTO RETRY ON" else "STREAM DOWN / AUTO RETRY OFF"
        else -> "NO ACTIVE STREAM"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FieldBody)
            .fieldGrid()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                /* ---- status header ---- */
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FieldText("DIAL", 22.sp, weight = FontWeight.Black, color = FieldInk, tracking = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BlinkingLed(size = 10.dp, mode = ledMode)
                            FieldText(statusWord, 11.sp, color = FieldInk)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(FieldInk))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FieldText(
                            "SELECTED " + (sel?.name ?: "NONE"),
                            10.sp, Modifier.weight(1f), color = FieldDim, tracking = 1.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        FieldText(
                            String.format(Locale.ROOT, "%02d STATIONS", stations.size),
                            10.sp, color = FieldDim, tracking = 1.sp,
                        )
                    }
                }

                /* ---- register panel ---- */
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(FieldInk, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BlinkingLed(size = 8.dp, mode = ledMode)
                            FieldText(
                                play?.name ?: sel?.name ?: "NO STATION",
                                16.sp, Modifier.weight(1f, fill = false), color = FieldPaper,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FieldText(
                            if (muted) "MUTED" else String.format(Locale.ROOT, "VOL %02d", vol),
                            10.sp, color = FieldDim, tracking = 1.sp,
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.End) {
                        FieldText(
                            play?.url ?: dimLine,
                            10.sp, Modifier.fillMaxWidth(), color = FieldDim, tracking = 1.sp,
                            align = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        FieldText(
                            clock(airtime),
                            22.sp, Modifier.padding(top = 4.dp), weight = FontWeight.Black,
                            color = if (phase == "on") FieldViolet else FieldPaper,
                        )
                    }
                    FieldText(
                        "AIR " + clock(airtime) + " / TOTAL " + clock(total),
                        10.sp, Modifier.padding(top = 12.dp), color = FieldViolet, tracking = 1.sp,
                    )
                }

                /* ---- control: volume + retry ---- */
                SectionHeader("CONTROL")
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldKey("VOL -", { muted = false; if (vol > 0) vol -= 1 }, Modifier.weight(1f), height = 40.dp, size = 12.sp)
                    FieldKey(
                        if (muted) "MUTE" else String.format(Locale.ROOT, "VOL %02d", vol),
                        { muted = !muted },
                        Modifier.weight(1f), height = 40.dp, size = 12.sp, active = muted,
                    )
                    FieldKey("VOL +", { muted = false; if (vol < 10) vol += 1 }, Modifier.weight(1f), height = 40.dp, size = 12.sp)
                }
                FieldToggleRow(
                    "RETRY",
                    "auto-reconnect when a stream drops",
                    retryOn,
                ) { retryOn = !retryOn }

                /* ---- transport ---- */
                SectionHeader("TRANSPORT")
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldKey(
                        "TUNE",
                        { if (play != null) stop() else if (sel != null) tune(sel!!) else msg = "SELECT A STATION" },
                        Modifier.weight(1f), height = 52.dp, size = 12.sp, variant = "accent",
                    )
                    FieldKey("STOP", { stop() }, Modifier.weight(1f), height = 52.dp, size = 12.sp)
                }

                /* ---- stations ---- */
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionHeader("STATIONS")
                    FieldText(msg, 10.sp, color = FieldDim, tracking = 1.sp)
                }
                /* input row: skin extension, foundation BasicTextField */
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(0.4f)
                            .height(52.dp)
                            .background(FieldPaper, RoundedCornerShape(3.dp))
                            .border(1.5.dp, FieldInk, RoundedCornerShape(3.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (nameIn.isEmpty()) FieldText("NAME", 12.sp, color = FieldDim, tracking = 1.sp)
                        BasicTextField(
                            value = nameIn,
                            onValueChange = { nameIn = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = FieldInk,
                            ),
                            singleLine = true,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(FieldPaper, RoundedCornerShape(3.dp))
                            .border(1.5.dp, FieldInk, RoundedCornerShape(3.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (urlIn.isEmpty()) FieldText("STREAM URL", 12.sp, color = FieldDim, tracking = 1.sp)
                        BasicTextField(
                            value = urlIn,
                            onValueChange = { urlIn = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = FieldInk,
                            ),
                            singleLine = true,
                        )
                    }
                    FieldKey("ADD", { addStation() }, Modifier.weight(0.3f), height = 52.dp, size = 12.sp, variant = "accent")
                }

                if (stations.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(FieldPaper, RoundedCornerShape(4.dp))
                            .border(1.5.dp, FieldInk, RoundedCornerShape(4.dp))
                            .padding(16.dp),
                    ) {
                        FieldText("NO STATIONS", 14.sp, color = FieldInk)
                        FieldText(
                            "ENTER A NAME AND STREAM URL, THEN ADD.",
                            10.sp, Modifier.padding(top = 4.dp), color = FieldDim, lineheight = 15.sp,
                        )
                    }
                } else {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stations.forEachIndexed { i, st ->
                            val isSel = st.id == selId
                            val isPlay = play?.id == st.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(FieldPaper, RoundedCornerShape(4.dp))
                                    .border(if (isSel) 2.dp else 1.5.dp, if (isSel) FieldViolet else FieldInk, RoundedCornerShape(4.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { if (isSel) tune(st) else { selId = st.id; prefs.edit().putInt("SEL", st.id).apply(); msg = "" } }
                                    .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.size(20.dp).border(1.5.dp, FieldInk),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FieldText("${i + 1}", 10.sp, color = FieldInk)
                                }
                                Column(Modifier.weight(1f)) {
                                    FieldText(
                                        st.name, 12.sp, color = FieldInk, tracking = 1.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    FieldText(
                                        st.url, 9.sp, Modifier.padding(top = 2.dp), color = FieldDim,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isPlay) BlinkingLed(size = 8.dp, mode = ledMode)
                            }
                        }
                        if (sel != null) {
                            FieldKey("DEL", { delStation() }, Modifier.fillMaxWidth(), height = 40.dp, size = 12.sp, variant = "ink")
                        }
                    }
                }

                /* ---- skin line ---- */
                FieldText(
                    "FIELD DESIGN LANGUAGE / SKIN: SIGNAL VIOLET / EXT: INPUT ROW",
                    10.sp, Modifier.padding(top = 6.dp), color = FieldDim, tracking = 1.sp,
                )
            }
        }
    }
}
