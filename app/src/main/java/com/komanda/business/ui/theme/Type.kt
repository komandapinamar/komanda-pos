package com.komanda.business.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.komanda.business.R

val InstrumentSansFontFamily = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold)
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = InstrumentSansFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = InstrumentSansFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = InstrumentSansFontFamily),
    headlineLarge = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = InstrumentSansFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = InstrumentSansFontFamily),
    titleLarge = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = InstrumentSansFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = InstrumentSansFontFamily),
    bodyLarge = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = InstrumentSansFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = InstrumentSansFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = InstrumentSansFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = InstrumentSansFontFamily)
)
