package com.admi.mifi.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admi.mifi.data.AppSettings
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.data.Md3ColorStyle
import com.admi.mifi.data.MiuixPalette
import com.admi.mifi.data.ThemeModeOption
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import androidx.compose.foundation.isSystemInDarkTheme

val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.MD3E }
val LocalLiquidGlass = staticCompositionLocalOf { true }

/** Distinct, slightly softened corners for the two visual languages. */
@Composable
fun mifiShapes(style: DesignStyle): Shapes = if (style == DesignStyle.MIUIX) {
    Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    )
} else {
    Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    )
}

@Composable
fun mifiTypography(style: DesignStyle): Typography {
    val base = Typography()
    return if (style == DesignStyle.MD3E) {
        Typography(
            displaySmall = base.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    } else {
        Typography(
            displaySmall = base.displaySmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.8).sp,
            ),
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.6).sp,
            ),
            headlineMedium = base.headlineMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp,
            ),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun MifiTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeModeOption.LIGHT -> false
        ThemeModeOption.DARK -> true
    }
    val context = LocalContext.current
    val useMonet = settings.designStyle == DesignStyle.MIUIX &&
        settings.monetEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (useMonet) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynamic.templateSurfaces(settings.designStyle, dark)
    } else {
        remember(
            settings.designStyle,
            settings.md3ColorStyle,
            settings.accentArgb,
            settings.palette,
            dark,
        ) {
            buildColorScheme(settings = settings, dark = dark)
        }
    }
    val resolvedScheme = if (
        settings.designStyle == DesignStyle.MIUIX &&
        !useMonet &&
        settings.palette == MiuixPalette.DEFAULT
    ) {
        // The reference template keeps the chosen accent crisp instead of toning it down.
        scheme.copy(
            primary = Color(settings.accentInt),
            onPrimary = Color.White,
            surfaceTint = Color(settings.accentInt),
        )
    } else {
        scheme
    }
    val shapes = mifiShapes(settings.designStyle)
    val typography = mifiTypography(settings.designStyle)
    CompositionLocalProvider(
        LocalDesignStyle provides settings.designStyle,
        LocalLiquidGlass provides settings.liquidGlass,
    ) {
        MaterialTheme(
            colorScheme = resolvedScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}

private fun buildColorScheme(
    settings: AppSettings,
    dark: Boolean,
): ColorScheme {
    val seed = Hct.fromInt(settings.accentArgb.toInt())
    val dynamic = when (settings.designStyle) {
        DesignStyle.MD3E -> when (settings.md3ColorStyle) {
            Md3ColorStyle.TONAL_SPOT -> SchemeTonalSpot(seed, dark, 0.0)
            Md3ColorStyle.VIBRANT -> SchemeVibrant(seed, dark, 0.0)
            Md3ColorStyle.EXPRESSIVE -> SchemeExpressive(seed, dark, 0.0)
            Md3ColorStyle.NEUTRAL -> SchemeNeutral(seed, dark, 0.0)
            Md3ColorStyle.MONOCHROME -> SchemeMonochrome(seed, dark, 0.0)
            Md3ColorStyle.FIDELITY -> SchemeFidelity(seed, dark, 0.0)
        }
        DesignStyle.MIUIX -> when (settings.palette) {
            MiuixPalette.DEFAULT -> SchemeFidelity(seed, dark, 0.0)
            MiuixPalette.MORANDI -> SchemeNeutral(seed, dark, 0.0)
            MiuixPalette.MACARON -> SchemeFruitSalad(seed, dark, 0.0)
            MiuixPalette.CYBER -> SchemeRainbow(seed, dark, 0.1)
            MiuixPalette.INK -> SchemeMonochrome(seed, dark, 0.0)
        }
    }
    return dynamic.toColorScheme().templateSurfaces(settings.designStyle, dark)
}

/**
 * MiuiX in the reference template stays deliberately neutral: a light grey canvas,
 * pure white cards and a single blue accent. MD3 Expressive keeps its tonal surfaces.
 */
private fun ColorScheme.templateSurfaces(style: DesignStyle, dark: Boolean): ColorScheme {
    if (style != DesignStyle.MIUIX) return this
    val neutral = if (dark) {
        Color(0xFF0F0F10)
    } else {
        Color(0xFFF3F3F5)
    }
    return copy(
        background = neutral,
        surface = neutral,
        onBackground = if (dark) Color(0xFFF4F4F6) else Color(0xFF111113),
        onSurface = if (dark) Color(0xFFF4F4F6) else Color(0xFF111113),
        surfaceVariant = if (dark) Color(0xFF2A2A2D) else Color(0xFFE8E8EB),
        onSurfaceVariant = if (dark) Color(0xFFA1A1A8) else Color(0xFF6D6D74),
        surfaceContainerLowest = if (dark) Color(0xFF1B1B1D) else Color(0xFFFFFFFF),
        surfaceContainerLow = if (dark) Color(0xFF202022) else Color(0xFFFCFCFD),
        surfaceContainer = if (dark) Color(0xFF252527) else Color(0xFFF0F0F2),
        surfaceContainerHigh = if (dark) Color(0xFF2C2C2F) else Color(0xFFE8E8EB),
        surfaceContainerHighest = if (dark) Color(0xFF343437) else Color(0xFFDEDEE2),
        surfaceBright = if (dark) Color(0xFF38383B) else Color(0xFFFFFFFF),
        surfaceDim = if (dark) Color(0xFF0C0C0D) else Color(0xFFE7E7EA),
        outline = if (dark) Color(0xFF8D8D93) else Color(0xFF898990),
        outlineVariant = if (dark) Color(0xFF3A3A3E) else Color(0xFFD5D5DA),
        inverseSurface = if (dark) Color(0xFFF4F4F6) else Color(0xFF2B2B2E),
        inverseOnSurface = if (dark) Color(0xFF1B1B1D) else Color(0xFFF4F4F6),
    )
}

private fun DynamicScheme.toColorScheme(): ColorScheme {
    fun c(value: Int) = Color(value)
    return if (isDark) {
        darkColorScheme(
            primary = c(primary),
            onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer),
            onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary),
            onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer),
            onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary),
            onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer),
            onTertiaryContainer = c(onTertiaryContainer),
            background = c(background),
            onBackground = c(onBackground),
            surface = c(surface),
            onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant),
            onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(surfaceTint),
            inverseSurface = c(inverseSurface),
            inverseOnSurface = c(inverseOnSurface),
            error = c(error),
            onError = c(onError),
            errorContainer = c(errorContainer),
            onErrorContainer = c(onErrorContainer),
            outline = c(outline),
            outlineVariant = c(outlineVariant),
            scrim = c(scrim),
            surfaceBright = c(surfaceBright),
            surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer),
            surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest),
            surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest),
        )
    } else {
        lightColorScheme(
            primary = c(primary),
            onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer),
            onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary),
            onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer),
            onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary),
            onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer),
            onTertiaryContainer = c(onTertiaryContainer),
            background = c(background),
            onBackground = c(onBackground),
            surface = c(surface),
            onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant),
            onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(surfaceTint),
            inverseSurface = c(inverseSurface),
            inverseOnSurface = c(inverseOnSurface),
            error = c(error),
            onError = c(onError),
            errorContainer = c(errorContainer),
            onErrorContainer = c(onErrorContainer),
            outline = c(outline),
            outlineVariant = c(outlineVariant),
            scrim = c(scrim),
            surfaceBright = c(surfaceBright),
            surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer),
            surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest),
            surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest),
        )
    }
}

