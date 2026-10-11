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

/** Big Type scale. Values from docs/SPEC.md §4. */
@Immutable
data class IntervalType(
    val countdown: TextStyle,
    val alarmLabel: TextStyle,
    val screenTitle: TextStyle,
    val sheetTitle: TextStyle,
    val reminderName: TextStyle,
    val body: TextStyle,
    val nextIn: TextStyle,
    val meta: TextStyle,
    val fieldLabel: TextStyle,
)

val DefaultType = IntervalType(
    countdown = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 61.sp, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
    alarmLabel = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 38.sp),
    screenTitle = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 0.01.em),
    sheetTitle = TextStyle(fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
    reminderName = TextStyle(fontFamily = ArchivoSlightlyWide, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    body = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    nextIn = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, fontFeatureSettings = "tnum"),
    meta = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    fieldLabel = TextStyle(fontFamily = ArchivoRegular, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.08.em),
)

val LocalIntervalType = staticCompositionLocalOf { DefaultType }
