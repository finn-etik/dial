/*
 * DIAL — web radio stream player in the Field Design Language.
 * Signal violet #6200EA.
 * Reason: each Field app carries a distinct signal accent; Relay owns orange.
 * EXT: INPUT ROW — BasicTextField station name/url inputs (foundation
 * primitive, styled per tokens: paper card, ink border, monospace).
 *
 * Playback: Media3 ExoPlayer (MP3 / AAC-ADTS / HLS / ICY streams).
 * Stations persist as JSON in SharedPreferences (org.json, framework only).
 * Station search: Radio Browser catalog API (radio-browser.info, CC0 data).
 * Auto-retry re-tunes 3 s after a stream error when RETRY is on.
 */

package com.field.dial

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun DialApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) }

    /* first run: seed with a few long-standing public streams */
    val seed = listOf(
        Station(1, "SOMA GROOVE", "https://ice1.somafm.com/groovesalad-128-mp3"),
        Station(2, "SOMA DRONE", "https://ice1.somafm.com/dronezone-128-mp3"),
        Station(3, "RADIO PARADISE", "https://stream.radioparadise.com/mp3-128"),
    )

    /* load once: stations, seeding and persisting on first run */
    val stations = remember {
        val list = mutableStateListOf<Station>()
        try {
            val arr = JSONArray(prefs.getString(Prefs.STATIONS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Station(o.getInt("id"), o.getString("n"), o.getString("u")))
            }
        } catch (_: Exception) {
            list.clear()
        }
        if (list.isEmpty() && !prefs.contains(Prefs.STATIONS)) {
            list.addAll(seed)
        }
        list
    }
    var idCounter by remember {
        mutableIntStateOf(stations.maxOfOrNull { it.id }?.plus(1) ?: 1)
    }

    var selId by remember { mutableIntStateOf(prefs.getInt(Prefs.SEL, -1)) }
    var play by remember { mutableStateOf<Station?>(null) }
    var phase by remember { mutableStateOf(Phase.OFF) }
    var airtime by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(prefs.getInt(Prefs.TOTAL, 0)) }
    var vol by remember { mutableIntStateOf(prefs.getInt(Prefs.VOL, 7)) }
    var muted by remember { mutableStateOf(false) }
    var retryOn by remember { mutableStateOf(prefs.getBoolean(Prefs.RETRY, true)) }
    var attempts by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var nameIn by remember { mutableStateOf("") }
    var urlIn by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<SearchHit>() }
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    fun saveStations() {
        val arr = JSONArray()
        stations.forEach { arr.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url)) }
        prefs.edit().putString(Prefs.STATIONS, arr.toString()).apply()
    }
    fun persistTotal() { prefs.edit().putInt(Prefs.TOTAL, total).apply() }
    fun volumeFraction(): Float = if (muted) 0f else vol / 10f

    fun releasePlayer() {
        player?.release()
        player = null
    }

    fun tune(st: Station) {
        releasePlayer()
        persistTotal()
        play = st
        selId = st.id
        prefs.edit().putInt(Prefs.SEL, st.id).apply()
        phase = Phase.CONNECT
        airtime = 0
        attempts = 0
        msg = ""
        val p = ExoPlayer.Builder(ctx).build()
        try {
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (player !== p) return
                    when (state) {
                        Player.STATE_READY -> { phase = Phase.ON; attempts = 0 }
                        Player.STATE_ENDED -> phase = Phase.ERR
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== p) return
                    phase = Phase.ERR
                }
            })
            p.setMediaItem(MediaItem.fromUri(st.url))
            p.setVolume(volumeFraction())
            p.playWhenReady = true
            p.prepare()
            player = p
        } catch (_: Exception) {
            p.release()
            phase = Phase.ERR
        }
    }

    fun stop() {
        releasePlayer()
        persistTotal()
        phase = Phase.OFF
        play = null
        airtime = 0
        attempts = 0
    }

    fun addStation() {
        val name = nameIn.trim().uppercase(Locale.ROOT)
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
        prefs.edit().putInt(Prefs.SEL, -1).apply()
        msg = "STATION DELETED"
    }

    /* search the Radio Browser catalog; tap a result to save it as a station */
    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) { msg = "TYPE A SEARCH TERM"; return }
        searching = true
        results.clear()
        scope.launch {
            val hits = try {
                RadioSearch.search(q)
            } catch (_: Exception) {
                searching = false
                msg = "SEARCH FAILED / CHECK NETWORK"
                return@launch
            }
            searching = false
            if (hits.isEmpty()) {
                msg = "NO STREAMS FOUND"
            } else {
                results.addAll(hits)
                msg = String.format(Locale.ROOT, "%02d STREAMS FOUND", hits.size)
            }
        }
    }

    fun addHit(hit: SearchHit) {
        if (stations.any { it.url.equals(hit.url, ignoreCase = true) }) { msg = "ALREADY SAVED"; return }
        stations.add(Station(idCounter++, hit.name, hit.url))
        saveStations()
        msg = "STATION SAVED"
    }

    /* persist volume + retry setting */
    LaunchedEffect(vol) { prefs.edit().putInt(Prefs.VOL, vol).apply() }
    LaunchedEffect(retryOn) { prefs.edit().putBoolean(Prefs.RETRY, retryOn).apply() }

    /* live volume changes */
    LaunchedEffect(vol, muted) {
        player?.setVolume(volumeFraction())
    }

    /* auto retry: re-tune 3 s after a stream error.
       keyed on retryOn too, so flipping RETRY while in ERR re-arms it. */
    LaunchedEffect(phase, retryOn) {
        if (phase == Phase.ERR && retryOn && play != null) {
            delay(3000)
            attempts += 1
            play?.let { tune(it) }
        }
    }

    /* airtime: 1 s tick while on air */
    LaunchedEffect(phase) {
        if (phase != Phase.ON) return@LaunchedEffect
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
        Phase.ON -> LedMode.SLOW
        Phase.CONNECT -> LedMode.FAST
        Phase.ERR -> LedMode.SOLID
        Phase.OFF -> LedMode.OFF
    }
    val statusWord = when (phase) {
        Phase.ON -> "ON AIR"
        Phase.CONNECT -> "CONNECT"
        Phase.ERR -> "ERR"
        Phase.OFF -> "STANDBY"
    }
    val dimLine = when (phase) {
        Phase.ON -> "STREAM LIVE / PLAYING"
        Phase.CONNECT -> if (attempts > 0) String.format(Locale.ROOT, "RETRY %02d / CONNECTING", attempts) else "CONNECTING TO STREAM"
        Phase.ERR -> if (retryOn) "STREAM DOWN / AUTO RETRY ON" else "STREAM DOWN / AUTO RETRY OFF"
        Phase.OFF -> "NO ACTIVE STREAM"
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
                .padding(WindowInsets.systemBars.asPaddingValues())
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
                            color = if (phase == Phase.ON) FieldViolet else FieldPaper,
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
                        { if (play != null) stop() else if (sel != null) tune(sel) else msg = "SELECT A STATION" },
                        Modifier.weight(1f), height = 52.dp, size = 12.sp, variant = KeyVariant.ACCENT,
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
                    FieldInput("NAME", nameIn, { nameIn = it }, Modifier.weight(0.4f))
                    FieldInput("STREAM URL", urlIn, { urlIn = it }, Modifier.weight(1f))
                    FieldKey("ADD", { addStation() }, Modifier.weight(0.3f), height = 52.dp, size = 12.sp, variant = KeyVariant.ACCENT)
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
                            10.sp, Modifier.padding(top = 4.dp), color = FieldDim, lineHeight = 15.sp,
                        )
                    }
                } else {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stations.forEachIndexed { i, st ->
                            val isSel = st.id == selId
                            StationRow(
                                index = i,
                                st = st,
                                isSel = isSel,
                                isPlay = play?.id == st.id,
                                ledMode = ledMode,
                            ) {
                                if (isSel) tune(st)
                                else { selId = st.id; prefs.edit().putInt(Prefs.SEL, st.id).apply(); msg = "" }
                            }
                        }
                        if (sel != null) {
                            FieldKey("DEL", { delStation() }, Modifier.fillMaxWidth(), height = 40.dp, size = 12.sp, variant = KeyVariant.INK)
                        }
                    }
                }

                /* ---- stream search (Radio Browser catalog) ---- */
                SectionHeader("SEARCH")
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldInput("SEARCH STREAMS", query, { query = it }, Modifier.weight(1f))
                    FieldKey("GO", { runSearch() }, Modifier.weight(0.3f), height = 52.dp, size = 12.sp, variant = KeyVariant.ACCENT)
                }
                if (searching) {
                    FieldText("SEARCHING...", 10.sp, Modifier.padding(top = 8.dp), color = FieldDim, tracking = 1.sp)
                }
                if (results.isNotEmpty()) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        results.forEach { hit -> SearchResultRow(hit) { addHit(hit) } }
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

@Composable
fun StationRow(index: Int, st: Station, isSel: Boolean, isPlay: Boolean, ledMode: LedMode, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FieldPaper, RoundedCornerShape(4.dp))
            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) FieldViolet else FieldInk, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).border(1.5.dp, FieldInk),
            contentAlignment = Alignment.Center,
        ) {
            FieldText("${index + 1}", 10.sp, color = FieldInk)
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

/* one search result; tap the row to save it as a station */
@Composable
fun SearchResultRow(hit: SearchHit, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FieldPaper, RoundedCornerShape(4.dp))
            .border(1.5.dp, FieldInk, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAdd,
            )
            .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            FieldText(hit.name, 12.sp, color = FieldInk, tracking = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FieldText(hit.url, 9.sp, Modifier.padding(top = 2.dp), color = FieldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                hit.country.takeIf { it.isNotEmpty() },
                if (hit.bitrate > 0) "${hit.bitrate} KBPS" else null,
                hit.tags.takeIf { it.isNotEmpty() }?.take(24),
            ).joinToString(" / ")
            if (meta.isNotEmpty()) {
                FieldText(meta, 9.sp, Modifier.padding(top = 2.dp), color = FieldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FieldText("ADD", 10.sp, color = FieldViolet, tracking = 1.sp)
    }
}
