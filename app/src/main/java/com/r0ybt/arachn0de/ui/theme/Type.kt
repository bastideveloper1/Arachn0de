package com.r0ybt.arachn0de.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
/** Explicit content sizes in sp; Android fontScale remains untouched. Controls keep label styles. */
data class ContentSizes(val cardTitle: Int, val cardTitleLine: Int, val description: Int, val descriptionLine: Int,
    val projectTitle: Int, val detail: Int, val detailLine: Int, val metadata: Int, val smallMetadata: Int, val scopeTitle: Int, val summary: Int)
internal fun contentSizes(size: AppearanceTextSize) = when (size) {
    AppearanceTextSize.Small -> ContentSizes(14,16,12,14,17,14,20,12,11,24,15)
    AppearanceTextSize.Medium -> ContentSizes(16,18,14,16,19,16,22,13,12,26,17)
    AppearanceTextSize.Large -> ContentSizes(18,20,16,18,21,18,24,14,13,28,19)
}
internal val LocalContentSizes = androidx.compose.runtime.staticCompositionLocalOf { contentSizes(AppearanceTextSize.Small) }
internal object ContentTypography {
    val ScopeTitle: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.scopeTitle.sp
    val Summary: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.summary.sp
    val CardTitle: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.cardTitle.sp
    val CardTitleLine: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.cardTitleLine.sp
    val Description: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.description.sp
    val DescriptionLine: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.descriptionLine.sp
    val ProjectTitle: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.projectTitle.sp
    val Detail: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.detail.sp
    val DetailLine: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.detailLine.sp
    val Metadata: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.metadata.sp
    val SmallMetadata: androidx.compose.ui.unit.TextUnit @androidx.compose.runtime.Composable get() = LocalContentSizes.current.smallMetadata.sp
}
internal fun appearanceTypography(size: AppearanceTextSize): Typography {
    val step = size.ordinal * 2
    if (step == 0) return Typography
    return Typography.copy(
        bodyLarge = Typography.bodyLarge.copy(fontSize = (16 + step).sp, lineHeight = (24 + step).sp),
        bodyMedium = Typography.bodyMedium.copy(fontSize = (14 + step).sp, lineHeight = (20 + step).sp),
        bodySmall = Typography.bodySmall.copy(fontSize = (12 + size.ordinal).sp, lineHeight = (16 + size.ordinal).sp),
        titleLarge = Typography.titleLarge.copy(fontSize = (22 + size.ordinal).sp, lineHeight = (28 + size.ordinal).sp),
        titleMedium = Typography.titleMedium.copy(fontSize = (16 + step).sp, lineHeight = (24 + step).sp),
        titleSmall = Typography.titleSmall.copy(fontSize = (14 + step).sp, lineHeight = (20 + step).sp),
    )
}
