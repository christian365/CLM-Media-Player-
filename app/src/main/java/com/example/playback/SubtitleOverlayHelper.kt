package com.example.playback

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.example.data.local.SubtitleStylePreset

data class SubtitleTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val mimeType: String,
    val isSelected: Boolean,
)

@OptIn(UnstableApi::class)
object SubtitleOverlayHelper {

    fun configureDefaultTextTrackSelection(
        trackSelector: DefaultTrackSelector,
        enableSubtitlesByDefault: Boolean = true,
    ) {
        val params = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enableSubtitlesByDefault)
            .setSelectUndeterminedTextLanguage(true)
            .build()
        trackSelector.parameters = params
    }

    fun extractSubtitleTracks(tracks: Tracks): List<SubtitleTrackOption> {
        val options = mutableListOf<SubtitleTrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIdx in 0 until group.length) {
                    val format = group.getTrackFormat(trackIdx)
                    val lang = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: "Default"
                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: buildString {
                            append("Track ${options.size + 1}")
                            if (lang != "Default") append(" ($lang)")
                            format.sampleMimeType?.let { mime ->
                                val shortMime = when {
                                    mime.contains("subrip") -> "SRT"
                                    mime.contains("ssa") -> "ASS/SSA"
                                    mime.contains("vtt") -> "WebVTT"
                                    mime.contains("cea608") -> "CEA-608"
                                    mime.contains("cea708") -> "CEA-708"
                                    else -> mime.substringAfterLast('/').uppercase()
                                }
                                append(" • $shortMime")
                            }
                        }
                    options.add(
                        SubtitleTrackOption(
                            groupIndex = groupIndex,
                            trackIndex = trackIdx,
                            label = label,
                            language = lang,
                            mimeType = format.sampleMimeType.orEmpty(),
                            isSelected = group.isTrackSelected(trackIdx),
                        )
                    )
                }
            }
        }
        return options
    }

    fun selectSubtitleTrack(
        player: androidx.media3.common.Player,
        option: SubtitleTrackOption?,
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex))
            )
        }
        player.trackSelectionParameters = builder.build()
    }

    fun applyStyle(
        subtitleView: SubtitleView?,
        textSizeSp: Float,
        preset: SubtitleStylePreset,
    ) {
        if (subtitleView == null) return
        val style = when (preset) {
            SubtitleStylePreset.CLASSIC_OUTLINE -> CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                Typeface.SANS_SERIF,
            )
            SubtitleStylePreset.HIGH_CONTRAST_BOX -> CaptionStyleCompat(
                Color.WHITE,
                Color.argb(215, 11, 14, 20),
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.BLACK,
                Typeface.DEFAULT_BOLD,
            )
            SubtitleStylePreset.AMBER_CINEMA -> CaptionStyleCompat(
                Color.rgb(253, 224, 71),
                Color.argb(120, 0, 0, 0),
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.BLACK,
                Typeface.SANS_SERIF,
            )
            SubtitleStylePreset.MINIMAL_TRANSLUCENT -> CaptionStyleCompat(
                Color.argb(240, 241, 245, 249),
                Color.argb(95, 15, 23, 42),
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.argb(180, 0, 0, 0),
                Typeface.SANS_SERIF,
            )
        }
        subtitleView.setStyle(style)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        subtitleView.setBottomPaddingFraction(0.08f)
    }
}
