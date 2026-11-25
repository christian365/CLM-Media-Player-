package com.example.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EqBand(
    val index: Short,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short,
)

data class AudioEffectsState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val selectedPresetIndex: Int = 0,
    val bands: List<EqBand> = emptyList(),
    val bassBoostStrength: Int = 0, // 0..1000
    val loudnessGainMb: Int = 0, // 0..1500
    val errorHint: String? = null,
)

class AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boundSessionId: Int = 0

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == boundSessionId) return
        releaseEffects()
        boundSessionId = audioSessionId

        runCatching {
            val eq = Equalizer(0, audioSessionId).apply { enabled = _state.value.isEnabled }
            val bandCount = eq.numberOfBands
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { (-1500).toShort() }
            val maxMb = range.getOrElse(1) { 1500.toShort() }

            val bands = (0 until bandCount).map { idx ->
                val bandIdx = idx.toShort()
                EqBand(
                    index = bandIdx,
                    centerFreqHz = eq.getCenterFreq(bandIdx) / 1000,
                    levelMb = eq.getBandLevel(bandIdx),
                    minLevelMb = minMb,
                    maxLevelMb = maxMb,
                )
            }
            val presets = (0 until eq.numberOfPresets).map { idx ->
                eq.getPresetName(idx.toShort())
            }

            equalizer = eq
            bassBoost = runCatching {
                BassBoost(0, audioSessionId).apply {
                    enabled = _state.value.isEnabled && _state.value.bassBoostStrength > 0
                    if (strengthSupported) {
                        setStrength(_state.value.bassBoostStrength.toShort())
                    }
                }
            }.getOrNull()
            loudnessEnhancer = runCatching {
                LoudnessEnhancer(audioSessionId).apply {
                    enabled = _state.value.isEnabled && _state.value.loudnessGainMb > 0
                    setTargetGain(_state.value.loudnessGainMb)
                }
            }.getOrNull()

            _state.update {
                it.copy(
                    isSupported = true,
                    presets = presets,
                    bands = bands,
                    errorHint = null,
                )
            }
        }.onFailure { err ->
            _state.update {
                it.copy(
                    isSupported = false,
                    errorHint = err.message ?: "Device hardware does not expose Equalizer session.",
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled && _state.value.bassBoostStrength > 0 }
        runCatching { loudnessEnhancer?.enabled = enabled && _state.value.loudnessGainMb > 0 }
        _state.update { it.copy(isEnabled = enabled) }
    }

    fun selectPreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            val updatedBands = _state.value.bands.map { band ->
                band.copy(levelMb = eq.getBandLevel(band.index))
            }
            _state.update {
                it.copy(selectedPresetIndex = presetIndex, bands = updatedBands)
            }
        }
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(bandIndex, levelMb)
            _state.update { current ->
                current.copy(
                    selectedPresetIndex = -1,
                    bands = current.bands.map {
                        if (it.index == bandIndex) it.copy(levelMb = levelMb) else it
                    },
                )
            }
        }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.apply {
                enabled = _state.value.isEnabled && clamped > 0
                if (strengthSupported) setStrength(clamped.toShort())
            }
        }
        _state.update { it.copy(bassBoostStrength = clamped) }
    }

    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1500)
        runCatching {
            loudnessEnhancer?.apply {
                enabled = _state.value.isEnabled && clamped > 0
                setTargetGain(clamped)
            }
        }
        _state.update { it.copy(loudnessGainMb = clamped) }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        boundSessionId = 0
    }
}
