package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassTier
import com.example.ui.theme.rememberGlassTokens

/** 1px separator, like macOS hairlines. */
fun Modifier.hairlineBottom(color: Color) = drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
}

/** Frosted translucent title bar with centered title + glass specular hairline. */
@Composable
fun MacToolbar(title: String, modifier: Modifier = Modifier) {
    val tokens = rememberGlassTokens(tier = GlassTier.CHROME)
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(tokens.fillBrush)
            .background(tokens.specularBrush)
            .hairlineBottom(hairline),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(20.dp, BlurredEdgeTreatment.Rectangle)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                        )
                    )
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Frosted Finder / Music-style sidebar for tablets & landscape. */
@Composable
fun MacSidebar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberGlassTokens(tier = GlassTier.CHROME)
    val items: List<Pair<ImageVector, String>> = listOf(
        Icons.Rounded.LibraryMusic to "Library",
        Icons.AutoMirrored.Rounded.QueueMusic to "Playlists",
        Icons.Rounded.Settings to "Settings",
    )
    Box(
        modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(tokens.fillBrush)
            .background(tokens.specularBrush)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(26.dp, BlurredEdgeTreatment.Rectangle)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    )
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "LIBRARY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
            )
            items.forEachIndexed { index, (icon, label) ->
                val selected = index == selectedIndex
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val itemShape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    itemShape,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
