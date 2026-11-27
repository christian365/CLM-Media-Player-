package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GlassTier
import com.example.ui.theme.LocalCadenceAppearance
import com.example.ui.theme.macGlassSurface

@Composable
fun macGlassBorderColor(): Color {
    val darkTheme = LocalCadenceAppearance.current.darkTheme
    return if (darkTheme) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
}

@Composable
fun macGlassSurfaceColor(alphaOverride: Float? = null): Color {
    val appearance = LocalCadenceAppearance.current
    val alpha = alphaOverride ?: if (appearance.hasDecorativeBackground) 0.56f else 0.74f
    return MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
}

@Composable
fun MacWindowTitleBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .macGlassSurface(shape = shape, tier = GlassTier.PANEL),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingActions,
            )
        }
    }
}

@Composable
fun <T> MacSegmentedStrip(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "mac_seg",
) {
    val scrollState = rememberScrollState()
    val stripShape = RoundedCornerShape(14.dp)
    Surface(
        shape = stripShape,
        color = Color.Transparent,
        modifier = modifier.macGlassSurface(shape = stripShape, tier = GlassTier.CARD),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    } else {
                        Color.Transparent
                    },
                    label = "mac_seg_pill",
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "mac_seg_text",
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(10.dp),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                        .testTag("${testTagPrefix}_${labelProvider(item).lowercase().replace(" ", "_")}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

enum class MacNavSection {
    LIBRARY,
    PLAYLISTS,
    SETTINGS,
}

@Composable
fun MacFloatingDock(
    activeSection: MacNavSection,
    onSelectSection: (MacNavSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = macGlassBorderColor()
    val appearance = LocalCadenceAppearance.current
    val dockColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = if (appearance.hasDecorativeBackground) 0.86f else 0.96f,
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = dockColor,
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .border(1.dp, border, RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MacDockItem(
                icon = Icons.Rounded.LibraryMusic,
                label = stringResource(R.string.nav_library),
                selected = activeSection == MacNavSection.LIBRARY,
                onClick = { onSelectSection(MacNavSection.LIBRARY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_library"),
            )
            MacDockItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = stringResource(R.string.nav_playlists),
                selected = activeSection == MacNavSection.PLAYLISTS,
                onClick = { onSelectSection(MacNavSection.PLAYLISTS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_playlists"),
            )
            MacDockItem(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.nav_settings),
                selected = activeSection == MacNavSection.SETTINGS,
                onClick = { onSelectSection(MacNavSection.SETTINGS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_settings"),
            )
        }
    }
}

@Composable
private fun MacDockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        } else {
            Color.Transparent
        },
        label = "dock_item_bg",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun MacFinderSidebar(
    activeSection: MacNavSection,
    onSelectSection: (MacNavSection) -> Unit,
    onOpenThemeStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = macGlassBorderColor()
    val appearance = LocalCadenceAppearance.current
    val sidebarBg = MaterialTheme.colorScheme.surfaceContainerLow.copy(
        alpha = if (appearance.hasDecorativeBackground) 0.84f else 0.98f,
    )

    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            sidebarBg,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "CLM MEDIA PLAYER",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )

            MacSidebarRow(
                icon = Icons.Rounded.LibraryMusic,
                label = stringResource(R.string.nav_library),
                selected = activeSection == MacNavSection.LIBRARY,
                onClick = { onSelectSection(MacNavSection.LIBRARY) },
                modifier = Modifier.testTag("nav_library"),
            )
            MacSidebarRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = stringResource(R.string.nav_playlists),
                selected = activeSection == MacNavSection.PLAYLISTS,
                onClick = { onSelectSection(MacNavSection.PLAYLISTS) },
                modifier = Modifier.testTag("nav_playlists"),
            )
            MacSidebarRow(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.nav_settings),
                selected = activeSection == MacNavSection.SETTINGS,
                onClick = { onSelectSection(MacNavSection.SETTINGS) },
                modifier = Modifier.testTag("nav_settings"),
            )

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = border)
            Spacer(Modifier.height(4.dp))

            MacSidebarRow(
                icon = Icons.Rounded.Palette,
                label = "Theme Studio",
                selected = false,
                onClick = onOpenThemeStudio,
                modifier = Modifier.testTag("sidebar_theme_studio"),
            )
        }
        VerticalDivider(thickness = 1.dp, color = border)
    }
}

@Composable
private fun MacSidebarRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        Color.Transparent
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
