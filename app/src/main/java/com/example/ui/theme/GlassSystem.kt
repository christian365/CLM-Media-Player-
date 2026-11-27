package com.example.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassTier {
    CHROME,
    PANEL,
    CARD,
    PLAYER_DECK,
    HUD_DARK,
}

@Immutable
data class GlassTokens(
    val baseColor: Color,
    val fillBrush: Brush,
    val specularBrush: Brush,
    val borderBrush: Brush,
    val subtleBlurRadius: Dp,
)

@Composable
fun rememberGlassTokens(
    tier: GlassTier,
    tintedPrimary: Boolean = false,
): GlassTokens {
    val appearance = LocalCadenceAppearance.current
    val dark = appearance.darkTheme
    val hasBg = appearance.hasDecorativeBackground
    val scheme = MaterialTheme.colorScheme

    return remember(tier, tintedPrimary, dark, hasBg, scheme) {
        if (tier == GlassTier.HUD_DARK) {
            val base = Color(0xFF10141D).copy(alpha = 0.58f)
            val fillBrush = Brush.verticalGradient(
                listOf(
                    Color(0xFF1E2534).copy(alpha = 0.66f),
                    Color(0xFF0E121A).copy(alpha = 0.58f),
                )
            )
            val specularBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.03f),
                    Color.Transparent,
                )
            )
            val borderBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.10f),
                )
            )
            return@remember GlassTokens(
                baseColor = base,
                fillBrush = fillBrush,
                specularBrush = specularBrush,
                borderBrush = borderBrush,
                subtleBlurRadius = 24.dp,
            )
        }

        val fillAlpha = when (tier) {
            GlassTier.CHROME -> if (dark) {
                if (hasBg) 0.56f else 0.72f
            } else {
                if (hasBg) 0.66f else 0.78f
            }
            GlassTier.PANEL -> if (dark) {
                if (hasBg) 0.52f else 0.68f
            } else {
                if (hasBg) 0.62f else 0.74f
            }
            GlassTier.CARD -> if (dark) {
                if (hasBg) 0.50f else 0.66f
            } else {
                if (hasBg) 0.60f else 0.72f
            }
            GlassTier.PLAYER_DECK -> if (dark) {
                if (hasBg) 0.54f else 0.70f
            } else {
                if (hasBg) 0.64f else 0.76f
            }
            GlassTier.HUD_DARK -> 0.58f
        }

        val topTint = if (tintedPrimary) {
            scheme.primaryContainer.copy(alpha = if (dark) fillAlpha + 0.10f else fillAlpha + 0.08f)
        } else if (dark) {
            scheme.surfaceContainerHigh.copy(alpha = fillAlpha)
        } else {
            Color.White.copy(alpha = (fillAlpha + 0.10f).coerceAtMost(0.92f))
        }

        val bottomTint = if (tintedPrimary) {
            scheme.surfaceContainer.copy(alpha = fillAlpha)
        } else if (dark) {
            scheme.surfaceContainerLow.copy(alpha = (fillAlpha + 0.06f).coerceAtMost(0.88f))
        } else {
            scheme.surfaceContainer.copy(alpha = fillAlpha)
        }

        val specularTopAlpha = when (tier) {
            GlassTier.PLAYER_DECK, GlassTier.PANEL -> if (dark) 0.12f else 0.42f
            else -> if (dark) 0.08f else 0.34f
        }

        val borderTopAlpha = if (tintedPrimary) {
            if (dark) 0.48f else 0.56f
        } else if (dark) {
            0.24f
        } else {
            0.84f
        }
        val borderBottomAlpha = if (tintedPrimary) {
            if (dark) 0.18f else 0.24f
        } else if (dark) {
            0.07f
        } else {
            0.28f
        }
        val borderTopColor = if (tintedPrimary) scheme.primary else Color.White
        val borderBottomColor = if (tintedPrimary) scheme.primary else Color.White

        GlassTokens(
            baseColor = bottomTint,
            fillBrush = Brush.verticalGradient(listOf(topTint, bottomTint)),
            specularBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = specularTopAlpha),
                    Color.White.copy(alpha = specularTopAlpha * 0.25f),
                    Color.Transparent,
                )
            ),
            borderBrush = Brush.verticalGradient(
                listOf(
                    borderTopColor.copy(alpha = borderTopAlpha),
                    borderBottomColor.copy(alpha = borderBottomAlpha),
                )
            ),
            subtleBlurRadius = when (tier) {
                GlassTier.PLAYER_DECK -> 32.dp
                GlassTier.CHROME -> 24.dp
                else -> 18.dp
            },
        )
    }
}

@Composable
fun Modifier.macGlassSurface(
    shape: Shape,
    tier: GlassTier = GlassTier.CARD,
    tintedPrimary: Boolean = false,
    borderWidth: Dp = 1.dp,
): Modifier {
    val tokens = rememberGlassTokens(tier = tier, tintedPrimary = tintedPrimary)
    return this
        .clip(shape)
        .background(tokens.fillBrush, shape)
        .background(tokens.specularBrush, shape)
        .border(borderWidth, tokens.borderBrush, shape)
}

/**
 * Frosted macOS glass container that renders an optional softly blurred ambient glow
 * behind a semi-transparent frosted fill, specular sheen, and luminous hairline border,
 * keeping all foreground content crisp and readable.
 */
@Composable
fun MacGlassContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.PLAYER_DECK,
    tintedPrimary: Boolean = false,
    ambientBitmap: ImageBitmap? = null,
    ambientTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = rememberGlassTokens(tier = tier, tintedPrimary = tintedPrimary)
    val dark = LocalCadenceAppearance.current.darkTheme

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, tokens.borderBrush, shape),
    ) {
        // Blurred ambient underlay (API 31+ RenderEffect blur; graceful layered tint below 31)
        if (ambientBitmap != null) {
            Image(
                bitmap = ambientBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (dark) 0.48f else 0.34f,
                modifier = Modifier
                    .matchParentSize()
                    .blur(tokens.subtleBlurRadius, BlurredEdgeTreatment.Rectangle),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(tokens.subtleBlurRadius, BlurredEdgeTreatment.Rectangle)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ambientTint.copy(alpha = if (dark) 0.16f else 0.14f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) 0.12f else 0.10f),
                            )
                        )
                    ),
            )
        }

        // Semi-transparent frosted glass body + top specular catchlight
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tokens.fillBrush)
                .background(tokens.specularBrush),
        )

        content()
    }
}

/**
 * Full-bleed blurred artwork backdrop for immersive player surfaces (e.g., Now Playing).
 */
@Composable
fun MacBlurredArtworkLayer(
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 56.dp,
) {
    val dark = LocalCadenceAppearance.current.darkTheme
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (dark) {
                                listOf(
                                    scheme.background.copy(alpha = 0.52f),
                                    scheme.background.copy(alpha = 0.66f),
                                    scheme.surfaceContainerLowest.copy(alpha = 0.80f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.46f),
                                    scheme.background.copy(alpha = 0.62f),
                                    scheme.surfaceContainerLow.copy(alpha = 0.78f),
                                )
                            }
                        )
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp, BlurredEdgeTreatment.Unbounded)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = if (dark) 0.30f else 0.24f),
                                scheme.secondary.copy(alpha = if (dark) 0.16f else 0.14f),
                                Color.Transparent,
                            )
                        )
                    ),
            )
        }
    }
}
