package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cadenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cadence_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themePalette: ThemePalette = ThemePalette.SKY_BLUE,
    val dynamicColorEnabled: Boolean = false,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.THEME_GLOW,
    val backgroundOverlayOpacity: Float = 0.45f,
    val customBackgroundPath: String? = null,
    val subtitlesEnabledByDefault: Boolean = true,
    val subtitleTextSizeSp: Float = 18f,
    val subtitleStylePreset: SubtitleStylePreset = SubtitleStylePreset.CLASSIC_OUTLINE,
    val preferExtensionDecoders: Boolean = false,
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BACKGROUND_PRESET = stringPreferencesKey("background_preset")
        val BACKGROUND_OVERLAY_OPACITY = floatPreferencesKey("background_overlay_opacity")
        val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val SUBTITLE_TEXT_SIZE = floatPreferencesKey("subtitle_text_size")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val PREFER_EXTENSION_DECODERS = booleanPreferencesKey("prefer_extension_decoders")
    }

    val preferences: Flow<UserPreferences> = context.cadenceDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            themePalette = prefs[Keys.THEME_PALETTE]
                ?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() }
                ?: ThemePalette.SKY_BLUE,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
            backgroundPreset = prefs[Keys.BACKGROUND_PRESET]
                ?.let { runCatching { BackgroundPreset.valueOf(it) }.getOrNull() }
                ?: BackgroundPreset.THEME_GLOW,
            backgroundOverlayOpacity = (prefs[Keys.BACKGROUND_OVERLAY_OPACITY] ?: 0.45f).coerceIn(0.15f, 0.85f),
            customBackgroundPath = prefs[Keys.CUSTOM_BACKGROUND_PATH]?.takeIf { it.isNotBlank() },
            subtitlesEnabledByDefault = prefs[Keys.SUBTITLES_ENABLED] ?: true,
            subtitleTextSizeSp = prefs[Keys.SUBTITLE_TEXT_SIZE] ?: 18f,
            subtitleStylePreset = prefs[Keys.SUBTITLE_STYLE]
                ?.let { runCatching { SubtitleStylePreset.valueOf(it) }.getOrNull() }
                ?: SubtitleStylePreset.CLASSIC_OUTLINE,
            preferExtensionDecoders = prefs[Keys.PREFER_EXTENSION_DECODERS] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.cadenceDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        context.cadenceDataStore.edit {
            it[Keys.THEME_PALETTE] = palette.name
            it[Keys.DYNAMIC_COLOR] = false
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setBackgroundPreset(preset: BackgroundPreset) {
        context.cadenceDataStore.edit { it[Keys.BACKGROUND_PRESET] = preset.name }
    }

    suspend fun setBackgroundOverlayOpacity(opacity: Float) {
        context.cadenceDataStore.edit {
            it[Keys.BACKGROUND_OVERLAY_OPACITY] = opacity.coerceIn(0.15f, 0.85f)
        }
    }

    suspend fun setCustomBackgroundPath(path: String?) {
        context.cadenceDataStore.edit {
            if (path.isNullOrBlank()) {
                it.remove(Keys.CUSTOM_BACKGROUND_PATH)
            } else {
                it[Keys.CUSTOM_BACKGROUND_PATH] = path
                it[Keys.BACKGROUND_PRESET] = BackgroundPreset.CUSTOM_PHOTO.name
            }
        }
    }

    suspend fun setSubtitlesEnabledByDefault(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.SUBTITLES_ENABLED] = enabled }
    }

    suspend fun setSubtitleTextSizeSp(sizeSp: Float) {
        context.cadenceDataStore.edit { it[Keys.SUBTITLE_TEXT_SIZE] = sizeSp.coerceIn(12f, 34f) }
    }

    suspend fun setSubtitleStylePreset(preset: SubtitleStylePreset) {
        context.cadenceDataStore.edit { it[Keys.SUBTITLE_STYLE] = preset.name }
    }

    suspend fun setPreferExtensionDecoders(prefer: Boolean) {
        context.cadenceDataStore.edit { it[Keys.PREFER_EXTENSION_DECODERS] = prefer }
    }
}
