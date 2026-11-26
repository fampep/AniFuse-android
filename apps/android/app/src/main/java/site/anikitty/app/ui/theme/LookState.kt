// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Surface depth on the monochrome AniFuse theme.
 * The accent is locked to white so saved colourful picks cannot leak back in.
 */
object LookState {
    private const val PREFS = "anikitty_look"

    val presets: List<Color> = listOf(Color.White)

    var accent by mutableStateOf(Color.White)
        private set
    /** "glass" = layered dark glass; "amoled" = flat true black. */
    var look by mutableStateOf("glass")
        private set
    var abandonedReminders by mutableStateOf(false)
        private set
    var weekGoalMinutes by mutableStateOf(120)
        private set
    var welcomed by mutableStateOf(false)
        private set
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        accent = Color.White
        look = prefs.getString("look", "glass")?.takeIf { it == "amoled" || it == "glass" } ?: "glass"
        abandonedReminders = prefs.getBoolean("abandoned_reminders", false)
        weekGoalMinutes = prefs.getInt("week_goal_minutes", 120).coerceIn(15, 600)
        welcomed = prefs.getBoolean("welcomed", false)
        loaded = true
    }

    fun setLook(context: Context, value: String) {
        look = value.takeIf { it == "amoled" || it == "glass" } ?: "glass"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("look", look)
            .apply()
    }

    fun setAccent(context: Context, color: Color) {
        // The theme stays white. The argument is kept so older call sites compile.
        accent = if (color.alpha == 0f && color != Color.Transparent) color else Color.White
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("accent", Color.White.toArgb())
            .apply()
    }

    fun setWeekGoal(context: Context, minutes: Int) {
        weekGoalMinutes = minutes.coerceIn(15, 600)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("week_goal_minutes", weekGoalMinutes)
            .apply()
    }

    fun setWelcomed(context: Context) {
        welcomed = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("welcomed", true)
            .apply()
    }

    /** Returns true the first time this ISO week hits the goal. */
    fun markWeekCelebrated(context: Context, weekKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("celebrated_week", "") == weekKey) return false
        prefs.edit().putString("celebrated_week", weekKey).apply()
        return true
    }

    fun setAbandonedReminders(context: Context, on: Boolean) {
        abandonedReminders = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("abandoned_reminders", on)
            .apply()
    }

    /** Ink on white controls. The [color] argument is ignored; the theme is white-on-black. */
    fun onAccent(color: Color = accent): Color =
        if (color.luminance() > 0.5f) Color.Black else Color.White
}
