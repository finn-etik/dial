package com.field.dial

import androidx.compose.ui.graphics.Color
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

/* playback state machine */
enum class Phase { OFF, CONNECT, ON, ERR }

/* LED blink pattern: SOLID = steady on, OFF = dark, FAST = 100/400, SLOW = 250/750 */
enum class LedMode { SOLID, OFF, FAST, SLOW }

/* FieldKey surface variants */
enum class KeyVariant { PAPER, ACCENT, INK }

/* station: one saved web stream */
data class Station(val id: Int, val name: String, val url: String)

/* one hit from the Radio Browser search API */
data class SearchHit(val name: String, val url: String, val country: String, val bitrate: Int, val tags: String)

fun clock(s: Int): String = String.format(
    Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60,
)
