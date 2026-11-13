package dev.glass.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * The entire settings store: three integers in one SharedPreferences file.
 * No DataStore, no LiveData, no listeners, no coroutines. Reads happen during
 * draw and cost one in-memory map lookup each.
 */
class GlassPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 0..40 dp. Drives the window background blur and the GPU shadow. */
    var blurRadius: Int
        get() = sp.getInt(KEY_BLUR, DEFAULT_BLUR).coerceIn(0, MAX_BLUR)
        set(value) = sp.edit().putInt(KEY_BLUR, value.coerceIn(0, MAX_BLUR)).apply()

    /** 0..60 percent. Feeds the frosted fill alpha and the AGSL gloss. */
    var tintPercent: Int
        get() = sp.getInt(KEY_TINT, DEFAULT_TINT).coerceIn(0, MAX_TINT)
        set(value) = sp.edit().putInt(KEY_TINT, value.coerceIn(0, MAX_TINT)).apply()

    /** 6..30 dp. Corner radius of every procedural glass tile. */
    var cornerRadius: Int
        get() = sp.getInt(KEY_CORNER, DEFAULT_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
        set(value) = sp.edit().putInt(KEY_CORNER, value.coerceIn(MIN_CORNER, MAX_CORNER)).apply()

    companion object {
        const val FILE = "glass"
        const val MAX_BLUR = 40
        const val MAX_TINT = 60
        const val MIN_CORNER = 6
        const val MAX_CORNER = 30
        const val DEFAULT_BLUR = 18
        const val DEFAULT_TINT = 24
        const val DEFAULT_CORNER = 18

        private const val KEY_BLUR = "blur"
        private const val KEY_TINT = "tint"
        private const val KEY_CORNER = "corner"
    }
}
