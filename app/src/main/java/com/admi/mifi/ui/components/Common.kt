package com.admi.mifi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.ui.theme.LocalDesignStyle
import kotlin.math.max

@Composable
fun MifiCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalDesignStyle.current
    val scheme = MaterialTheme.colorScheme
    val color = when {
        containerColor != Color.Unspecified -> containerColor
        style == DesignStyle.MIUIX -> scheme.surfaceContainerLowest
        else -> scheme.surfaceContainerLow
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style = LocalDesignStyle.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (style == DesignStyle.MIUIX) {
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Normal,
                    )
                } else {
                    MaterialTheme.typography.headlineLarge
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val style = LocalDesignStyle.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = if (style == DesignStyle.MIUIX) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == DesignStyle.MIUIX) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurface.copy(alpha = 0.86f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (accent == scheme.primary) scheme.onSurfaceVariant else accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onCopy != null && value != "—") {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "复制$label",
                    modifier = Modifier.size(17.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier, color: Color? = null) {
    val dotColor = color ?: if (active) Color(0xFF2FBF71) else MaterialTheme.colorScheme.outline
    Box(modifier = modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .background(dotColor.copy(alpha = 0.16f), CircleShape),
        )
        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
    }
}

@Composable
fun SignalBars(
    level: Int,
    dbm: Int?,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val active = if (level > 0) {
        level.coerceIn(1, 4)
    } else {
        when {
            dbm == null -> 0
            dbm >= -65 -> 4
            dbm >= -80 -> 3
            dbm >= -95 -> 2
            dbm >= -105 -> 1
            else -> 0
        }
    }
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(5.dp)
                    .height((7 + index * 4).dp)
                    .background(
                        color = if (index < active) activeColor else inactive,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
fun MiniTag(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val style = LocalDesignStyle.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (style == DesignStyle.MIUIX) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        if (style == DesignStyle.MIUIX) {
            val iconTint = if (tint == MaterialTheme.colorScheme.primary) {
                MaterialTheme.colorScheme.onSurface
            } else {
                tint
            }
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                    modifier = Modifier.size(23.dp),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    maxLines = 1,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(tint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
fun Sparkline(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val maxValue = max(1L, values.maxOrNull() ?: 0L)
        val stepX = size.width / max(1, values.size - 1)
        val bottom = size.height
        val usableHeight = size.height - 4.dp.toPx()
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = bottom - (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) * usableHeight
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, bottom)
        fillPath.close()
        if (fill) {
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                    startY = 0f,
                    endY = bottom,
                ),
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
fun DualSparkline(
    downValues: List<Long>,
    upValues: List<Long>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val all = downValues + upValues
        val maxValue = max(1L, all.maxOrNull() ?: 0L)
        val count = max(downValues.size, upValues.size)
        if (count < 2) return@Canvas
        val stepX = size.width / (count - 1)
        val usableHeight = size.height - 4.dp.toPx()

        fun build(values: List<Long>): Path {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = (index + (count - values.size)) * stepX
                val y = size.height - (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) * usableHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        val downPath = build(downValues)
        val upPath = build(upValues)
        val downFill = Path().apply {
            addPath(downPath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = downFill,
            brush = Brush.verticalGradient(
                listOf(downColor.copy(alpha = 0.22f), Color.Transparent),
                endY = size.height,
            ),
        )
        drawPath(downPath, downColor, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(upPath, upColor, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun TrafficDonut(
    downBytes: Long,
    upBytes: Long,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val total = (downBytes + upBytes).coerceAtLeast(0)
    val fraction = if (total > 0) downBytes.toFloat() / total else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(700),
        label = "trafficFraction",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (total > 0) {
                val downSweep = 360f * animated
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = (downSweep - 3f).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = tertiary,
                    startAngle = -90f + downSweep,
                    sweepAngle = ((360f - downSweep) - 3f).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Default,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SelectableIndicator(selected: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape))
        }
    }
}

@Composable
fun ClickableSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val style = LocalDesignStyle.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (style == DesignStyle.MIUIX) 2.dp else 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(if (style == DesignStyle.MIUIX) 13.dp else 14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (style == DesignStyle.MIUIX) FontWeight.Medium else FontWeight.Normal,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
