package com.admi.mifi.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.ui.MainTab
import com.admi.mifi.ui.components.template.FloatingBottomBar
import com.admi.mifi.ui.components.template.FloatingBottomBarItem
import com.admi.mifi.ui.theme.LocalDesignStyle
import com.admi.mifi.ui.theme.LocalLiquidGlass
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

@Composable
fun MifiBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: HazeState?,
    liquidBackdrop: LayerBackdrop?,
    floating: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = LocalDesignStyle.current
    val scheme = MaterialTheme.colorScheme
    val miuix = style == DesignStyle.MIUIX
    val glassEnabled = LocalLiquidGlass.current
    val dark = scheme.surface.luminance() < 0.4f

    val hazeStyle =
        remember(scheme.surfaceContainerLowest) {
            HazeStyle(
                backgroundColor = scheme.surfaceContainerLowest.copy(alpha = 0.78f),
                tints = listOf(HazeTint(scheme.surfaceContainerLowest.copy(alpha = 0.35f))),
                blurRadius = 30.dp,
                noiseFactor = 0.02f,
                fallbackTint = HazeTint(scheme.surfaceContainerLowest.copy(alpha = 0.96f)),
            )
        }

    when {
        miuix -> {
            val liquid =
                glassEnabled && liquidBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val blurEnabled = liquid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val fallbackGlass = glassEnabled && !liquid && hazeState != null
            val navigationBottom =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val localBackdrop = rememberLayerBackdrop()
            val pageBackdrop = liquidBackdrop ?: localBackdrop
            val selectedIndex = remember(selected) { { selected.ordinal } }
            val onIndexSelected =
                remember(onSelect) { { index: Int -> onSelect(MainTab.entries[index]) } }

            var barModifier = modifier.padding(bottom = 12.dp + navigationBottom)
            if (fallbackGlass) {
                barModifier = barModifier.hazeEffect(hazeState!!, style = hazeStyle)
            }
            val containerColor =
                when {
                    blurEnabled || fallbackGlass -> {
                        if (dark) Color(0xFF121212).copy(alpha = 0.40f)
                        else Color(0xFFFAFAFA).copy(alpha = 0.40f)
                    }
                    else -> {
                        if (dark) Color(0xFF121212).copy(alpha = 0.72f)
                        else Color(0xFFFAFAFA).copy(alpha = 0.86f)
                    }
                }
            FloatingBottomBar(
                modifier = barModifier,
                selectedIndex = selectedIndex,
                onSelected = onIndexSelected,
                backdrop = pageBackdrop,
                tabsCount = MainTab.entries.size,
                isBlurEnabled = blurEnabled,
                containerColor = containerColor,
                accentColor = scheme.primary,
                dark = dark,
            ) {
                MainTab.entries.forEach { tab ->
                    FloatingBottomBarItem(
                        onClick = { onSelect(tab) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(tab.icon, tab.label, tint = scheme.onSurface)
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }

        floating -> {
            Md3FloatingBottomBar(
                modifier = modifier,
                selected = selected,
                onSelect = onSelect,
            )
        }

        else -> {
            NavigationBar(
                modifier = modifier.fillMaxWidth(),
                containerColor = scheme.surfaceContainer,
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, tab.label) },
                        label = {
                            Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive floating navigation bar for MD3E.
 *
 * Deliberately different from the MiuiX glass capsule: a solid tonal surface
 * with a small `secondaryContainer` active indicator behind the icon only,
 * Material typography and a spring that slides the indicator between tabs.
 */
@Composable
private fun Md3FloatingBottomBar(
    modifier: Modifier,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(28.dp)
    val indicatorShape = RoundedCornerShape(percent = 50)
    val tabCount = MainTab.entries.size

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val tabWidthPx = if (rowWidthPx > 0f) rowWidthPx / tabCount else 0f
    val indicatorWidth = 56.dp
    val indicatorHeight = 32.dp
    val indicatorWidthPx = with(density) { indicatorWidth.toPx() }

    val indicator = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected) {
        indicator.animateTo(
            targetValue = selected.ordinal.toFloat(),
            animationSpec = spring(
                stiffness = 480f,
                dampingRatio = 0.78f,
                visibilityThreshold = 0.001f,
            ),
        )
    }

    Surface(
        modifier = modifier
            .padding(bottom = 10.dp + navBottom)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(72.dp),
        shape = shape,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (
                                    indicator.value * tabWidthPx +
                                        (tabWidthPx - indicatorWidthPx) / 2f
                                    ).roundToInt(),
                                y = 0,
                            )
                        }
                        .padding(top = 10.dp)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .clip(indicatorShape)
                        .background(scheme.secondaryContainer),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rowWidthPx = it.size.width.toFloat() },
                verticalAlignment = Alignment.Top,
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    val selection by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = spring(
                            stiffness = 500f,
                            dampingRatio = 0.8f,
                            visibilityThreshold = 0.001f,
                        ),
                        label = "md3-tab-selection",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(tab) },
                                role = Role.Tab,
                            )
                            .padding(top = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(indicatorHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = lerp(
                                    scheme.onSurfaceVariant,
                                    scheme.onSecondaryContainer,
                                    selection,
                                ),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        val scale = 1f + 0.08f * selection
                                        scaleX = scale
                                        scaleY = scale
                                    },
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = lerp(scheme.onSurfaceVariant, scheme.onSurface, selection),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}
