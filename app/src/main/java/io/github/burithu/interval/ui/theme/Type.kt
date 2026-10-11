package io.github.burithu.interval.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.burithu.interval.R

/**
 * Archivo is one variable font with weight and width axes. Each width used by the
 * design gets its own FontFamily so weights resolve correctly within it.
 */
private fun archivo(width: Float) = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            resId = R.font.archivo_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.width(width),
            ),
        )
    },
)

val ArchivoCondensed = archivo(70f)
val ArchivoNarrow = archivo(80f)
val ArchivoRegular = archivo(100f)
val ArchivoSlightlyWide = archivo(105f)
val ArchivoWide = archivo(112f)

/**
 * Big Type scale, 11 sizes (docs/SPEC.md §4). Screens use these as they are; no inline
 * size overrides.
 */
@Immutable
data class IntervalType(
    val display: TextStyle,
    val countdown: TextStyle,
    /** The "h" and "min" inside [countdown], applied as a span. */
    val countdownUnit: TextStyle,
    val alarmLabel: TextStyle,
    val time: TextStyle,
    val numeral: TextStyle,
    val screenTitle: TextStyle,
    val title: TextStyle,
    val sheetTitle: TextStyle,
    val nextIn: TextStyle,
    val reminderName: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val fieldLabel: TextStyle,
)

val DefaultType = IntervalType(
    display = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, lineHeight = 76.sp, letterSpacing = (-0.02).em),
    countdown = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 61.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
    countdownUnit = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.sp),
    alarmLabel = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 46.sp),
    time = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
    numeral = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
    screenTitle = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 0.01.em),
    title = TextStyle(fontFamily = ArchivoSlightlyWide, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    sheetTitle = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
    nextIn = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, fontFeatureSettings = "tnum"),
    reminderName = TextStyle(fontFamily = ArchivoSlightlyWide, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    body = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    meta = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    fieldLabel = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.08.em),
)

val LocalIntervalType = staticCompositionLocalOf { DefaultType }
