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
    val light:Boolean=false,
)
private fun palette(background: Long, middle: Long, end: Long, surface: Long, raised: Long,
    text: Long, muted: Long, primary: Long, secondary: Long, solid: Long,
    outline: Long, controls: Long, selection: Long, accentSurface: Long, scrim: Long = background) =
    AppearancePalette(Color(background), Color(middle), Color(end), Color(scrim), Color(surface), Color(raised),
        Color(text), Color(muted), Color(primary), Color(secondary), Color(solid), Color(outline), Color(controls), Color(selection), Color(accentSurface))

enum class AppearanceTheme(val label: String, val palette: AppearancePalette) {
    Arachn0de("Black & White 90", BlackWhite90.palette),
    KingCrimson("King Crimson", palette(0xFF0C080A,0xFF100A0D,0xFF150D11,0xFF1D1519,0xFF171014,
        0xFFF7EEF1,0xFFD4BBC3,0xFFD65B78,0xFFC98578,0xFF70263D,0xFF4A2933,0xFF30232A,0xFF593341,0xFF34202A)),
    DeepMoss("Deep Moss", palette(0xFF080C0A,0xFF0B100D,0xFF0F1511,0xFF171E19,0xFF121813,
        0xFFF0F4ED,0xFFBDC8B7,0xFFA2B595,0xFF929B78,0xFF546C4A,0xFF364533,0xFF273126,0xFF3D5037,0xFF24301F)),
    HeavyGold("Heavy Gold", palette(0xFF0D0B08,0xFF110E0A,0xFF16120D,0xFF1C1915,0xFF171410,
        0xFFF5F0E7,0xFFC8BEAD,0xFFC7B58C,0xFFB28D76,0xFF675737,0xFF4D4332,0xFF302B24,0xFF49402D,0xFF2D271D)),
    NightBlue("Azul noche", AppearancePalette(
        background=Color(0xFF1B2634),middle=Color(0xFF1B2634),end=Color(0xFF1B2634),scrim=Color(0xFF1B2634),
        surface=Color(0xFF2B3B4F),raised=Color(0xFF3B526B),text=Color(0xFFEDF5FF),muted=Color(0xFFA7C8E5),
        primary=Color(0xFF8EC5F4),secondary=Color(0xFFA7C8E5),solidPrimary=Color(0xFF3B526B),
        outline=Color(0xFFA7C8E5),controls=Color(0xFF3B526B),selection=Color(0xFF3B526B),accentSurface=Color(0xFF3B526B),onSolidPrimary=Color(0xFFEDF5FF))),
    DayGrey("Gris día", AppearancePalette(
        background=Color(0xFFD9DDE2),middle=Color(0xFFD9DDE2),end=Color(0xFFD9DDE2),scrim=Color(0xFFD9DDE2),
        surface=Color(0xFFEDF0F2),raised=Color(0xFFD4D9DE),text=Color(0xFF292F35),muted=Color(0xFF41474E),
        primary=Color(0xFF515963),secondary=Color(0xFF41474E),solidPrimary=Color(0xFF515963),
        outline=Color(0xFF515963),controls=Color(0xFFD4D9DE),selection=Color(0xFFD4D9DE),accentSurface=Color(0xFFD4D9DE),onSolidPrimary=Color(0xFFEDF0F2),light=true)),

}
enum class AppearanceTextSize(val label: String) { Small("Pequeña"), Medium("Mediana"), Large("Grande") }
enum class IndicatorColor(val label:String,val color:Color) {
    WHITE("Blanco",BlackWhite90.Text), GREY("Gris",Color(0xFF8D8D8D)), RED("Rojo",Color(0xFFFF8C8C)),
    ORANGE("Naranja",Color(0xFFFFAD70)), YELLOW("Amarillo",Color(0xFFE6D477)), GREEN("Verde",Color(0xFF98CA9D)),
    BLUE("Azul",Color(0xFF83BFFF)), VIOLET("Violeta",Color(0xFFC3A1E6))
}
internal val cardIndicatorNames=setOf("people","technologies","notes","tasks","files","due","today","high")
data class AppearanceSettings(val theme: AppearanceTheme = AppearanceTheme.Arachn0de, val textSize: AppearanceTextSize = AppearanceTextSize.Small, val indicatorColors:Map<String,IndicatorColor> = emptyMap())

/** Reads before the first composition; apply updates memory immediately and persists locally. */
class AppearancePreferences(private val storage: SharedPreferences) {
    private fun read():AppearanceSettings {
        if(storage.all[THEME]=="Minimalist") check(storage.edit().putString(THEME,AppearanceTheme.Arachn0de.name).commit()) {"No se pudo conservar la apariencia."}
        return AppearanceSettings(AppearanceTheme.entries.firstOrNull {it.name==storage.all[THEME]} ?: AppearanceTheme.Arachn0de,
            AppearanceTextSize.entries.firstOrNull {it.name==storage.all[TEXT]} ?: AppearanceTextSize.Small,
            cardIndicatorNames.mapNotNull {name->IndicatorColor.entries.firstOrNull {it.name==storage.all[INDICATOR+name]}?.let {name to it}}.toMap())
    }
    var settings by mutableStateOf(read())
        private set
    fun reload() {settings=read()}
    fun observe(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener) {storage.registerOnSharedPreferenceChangeListener(listener)}
    fun unobserve(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener) {storage.unregisterOnSharedPreferenceChangeListener(listener)}
    fun setTheme(theme: AppearanceTheme) { storage.edit().putString(THEME, theme.name).apply();settings = settings.copy(theme = theme) }
    fun setTextSize(size: AppearanceTextSize) { storage.edit().putString(TEXT, size.name).apply();settings = settings.copy(textSize = size) }
    fun setIndicatorColor(name:String,color:IndicatorColor) {
        require(name in cardIndicatorNames)
        storage.edit().putString(INDICATOR+name,color.name).apply()
        settings=settings.copy(indicatorColors=settings.indicatorColors+(name to color))
    }
    fun resetIndicatorColors() {
        val editor=storage.edit();cardIndicatorNames.forEach {editor.remove(INDICATOR+it)};editor.apply()
        settings=settings.copy(indicatorColors=emptyMap())
    }
    companion object {
        private const val INDICATOR="indicator_color_"
        fun validate(payload:String) {
            val values=com.r0ybt.arachn0de.security.EncryptedPreferences.decode(payload)
            require(THEME !in values || values[THEME] is String) {"Tema inválido."}
            require(TEXT !in values || values[TEXT] is String) {"Tamaño de texto inválido."}
            values.filterKeys {it.startsWith(INDICATOR)}.forEach {(key,value)->
                require(key.removePrefix(INDICATOR) in cardIndicatorNames && IndicatorColor.entries.any {it.name==value}) {"Color de indicador inválido."}
            }
        }
        const val FILE = "appearance_preferences"; private const val THEME = "theme"; private const val TEXT = "text_size" }
}
internal val LocalAppearancePreferences = staticCompositionLocalOf<AppearancePreferences?> { null }
internal val LocalAppearancePalette = staticCompositionLocalOf { AppearanceTheme.Arachn0de.palette }

/** Regenerable device cache: only the primary theme name, never credentials or store IDs. */
internal object UnlockAppearanceCache {
    const val FILE="appearance_unlock_cache"
    private const val KEY="theme"
    fun read(context:android.content.Context)=AppearanceTheme.entries.firstOrNull {it.name==context.getSharedPreferences(FILE,0).all[KEY]} ?: AppearanceTheme.Arachn0de
    fun update(context:android.content.Context,theme:AppearanceTheme) {
        val storage=context.getSharedPreferences(FILE,0)
        if(storage.all[KEY]!=theme.name) storage.edit().putString(KEY,theme.name).apply()
    }
}
