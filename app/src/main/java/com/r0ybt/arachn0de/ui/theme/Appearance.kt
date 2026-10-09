package com.r0ybt.arachn0de.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Identity only. Functional status colors live in SemanticColors, outside every palette. */
data class AppearancePalette(
    val background: Color, val middle: Color, val end: Color, val scrim: Color,
    val surface: Color, val raised: Color, val text: Color, val muted: Color,
    val primary: Color, val secondary: Color, val solidPrimary: Color,
    val outline: Color, val controls: Color, val selection: Color, val accentSurface: Color,
    val onSolidPrimary: Color = Color.White,
)
private fun palette(background: Long, middle: Long, end: Long, surface: Long, raised: Long,
    text: Long, muted: Long, primary: Long, secondary: Long, solid: Long,
    outline: Long, controls: Long, selection: Long, accentSurface: Long, scrim: Long = background) =
    AppearancePalette(Color(background), Color(middle), Color(end), Color(scrim), Color(surface), Color(raised),
        Color(text), Color(muted), Color(primary), Color(secondary), Color(solid), Color(outline), Color(controls), Color(selection), Color(accentSurface))

enum class AppearanceTheme(val label: String, val palette: AppearancePalette) {
    Arachn0de("Arachn0de", palette(0xFF000000,0xFF050505,0xFF0A0A0A,0xFF17181C,0xFF121316,
        0xFFF3F6FF,0xFFB9C6E8,0xFFB99BFF,0xFFFFA45B,0xFF7E61FF,0xFF3A2C52,0xFF2A2A2D,0xFF4B3E76,0xFF2A1F39,0xFF090A0D).copy(onSolidPrimary = Color.Black)),
    KingCrimson("King Crimson", palette(0xFF0C080A,0xFF100A0D,0xFF150D11,0xFF1D1519,0xFF171014,
        0xFFF7EEF1,0xFFD4BBC3,0xFFD65B78,0xFFC98578,0xFF70263D,0xFF4A2933,0xFF30232A,0xFF593341,0xFF34202A)),
    DeepMoss("Deep Moss", palette(0xFF080C0A,0xFF0B100D,0xFF0F1511,0xFF171E19,0xFF121813,
        0xFFF0F4ED,0xFFBDC8B7,0xFFA2B595,0xFF929B78,0xFF546C4A,0xFF364533,0xFF273126,0xFF3D5037,0xFF24301F)),
    HeavyGold("Heavy Gold", palette(0xFF0D0B08,0xFF110E0A,0xFF16120D,0xFF1C1915,0xFF171410,
        0xFFF5F0E7,0xFFC8BEAD,0xFFC7B58C,0xFFB28D76,0xFF675737,0xFF4D4332,0xFF302B24,0xFF49402D,0xFF2D271D)),
    Minimalist("Minimalist", palette(0xFF000000,0xFF050505,0xFF0A0A0A,0xFF181818,0xFF121212,
        0xFFF4F4F4,0xFFBDBDBD,0xFFD9D9D9,0xFFAFAFAF,0xFF575757,0xFF3D3D3D,0xFF2A2A2A,0xFF444444,0xFF272727)),
}
enum class AppearanceTextSize(val label: String) { Small("Pequeña"), Medium("Mediana"), Large("Grande") }
data class AppearanceSettings(val theme: AppearanceTheme = AppearanceTheme.Arachn0de, val textSize: AppearanceTextSize = AppearanceTextSize.Small)

/** Reads before the first composition; apply updates memory immediately and persists locally. */
class AppearancePreferences(private val storage: SharedPreferences) {
    var settings by mutableStateOf(AppearanceSettings(
        AppearanceTheme.entries.firstOrNull { it.name == storage.all[THEME] } ?: AppearanceTheme.Arachn0de,
        AppearanceTextSize.entries.firstOrNull { it.name == storage.all[TEXT] } ?: AppearanceTextSize.Small))
        private set
    fun reload() { settings=AppearanceSettings(
        AppearanceTheme.entries.firstOrNull {it.name==storage.all[THEME]} ?: AppearanceTheme.Arachn0de,
        AppearanceTextSize.entries.firstOrNull {it.name==storage.all[TEXT]} ?: AppearanceTextSize.Small) }
    fun observe(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener) {storage.registerOnSharedPreferenceChangeListener(listener)}
    fun unobserve(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener) {storage.unregisterOnSharedPreferenceChangeListener(listener)}
    fun setTheme(theme: AppearanceTheme) { settings = settings.copy(theme = theme); storage.edit().putString(THEME, theme.name).apply() }
    fun setTextSize(size: AppearanceTextSize) { settings = settings.copy(textSize = size); storage.edit().putString(TEXT, size.name).apply() }
    companion object { const val FILE = "appearance_preferences"; private const val THEME = "theme"; private const val TEXT = "text_size" }
}
internal val LocalAppearancePreferences = staticCompositionLocalOf<AppearancePreferences?> { null }
internal val LocalAppearancePalette = staticCompositionLocalOf { AppearanceTheme.Arachn0de.palette }
