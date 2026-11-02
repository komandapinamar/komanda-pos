package com.komanda.business.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized Komanda design tokens mirroring Komanda Web globals.css
 * (Dabbit High-Contrast Liquid Glass & Prismatic Ring).
 */
object KomandaTokens {
    // Surfaces & Backgrounds
    val Background = Color(0xFF141416)       // --dabbit-bg
    val SurfaceDeep = Color(0xFF101013)      // Deep recessed input / nested surface
    val Surface = Color(0xFF18181C)          // --dabbit-card / .liquid-glass-card
    val SurfaceVariant = Color(0xFF1E1E24)   // --dabbit-muted
    val SurfaceGlass = Color(0xED141418)     // rgba(20, 20, 24, 0.93)
    val SurfacePill = Color(0x0FFFFFFF)      // rgba(255, 255, 255, 0.06)
    val Border = Color(0x24FFFFFF)           // rgba(255, 255, 255, 0.14)
    val BorderSubtle = Color(0x14FFFFFF)     // rgba(255, 255, 255, 0.08)

    // Text & Content
    val TextPrimary = Color(0xFFF4F3F1)      // --dabbit-fg
    val TextSecondary = Color(0xFFC2C2CC)    // --dabbit-muted-fg
    val TextMuted = Color(0xFF9B9BA4)

    // Design System Accents (mirrors Komanda web: --color-accent-primary/secondary/tertiary)
    val AccentPrimary = Color(0xFF141416)
    val AccentSecondary = Color(0xFFF4F3F1)
    val AccentTertiary = Color(0xFFF4F3F1)

    // Chromatic Prism Ring (mirrors .liquid-prism-ring conic-gradient)
    val PrismRingBrush = Brush.sweepGradient(
        0.00f to Color(0x6BFFFFFF),
        0.15f to Color(0x999AB3D5),
        0.33f to Color(0x99D97757),
        0.51f to Color(0x80E3B878),
        0.70f to Color(0x8C9EC4B5),
        0.86f to Color(0x80B5A5C7),
        1.00f to Color(0x6BFFFFFF)
    )

    val LiquidGlassBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xF01C1C22),
            Color(0xEB141418),
            Color(0xF016161B)
        )
    )

    // Operational Status (Kitchen KDS & Despacho)
    val StatusPreparing = Color(0xFFF59E0B)
    val StatusPreparingBg = Color(0x26F59E0B)
    val StatusReady = Color(0xFF10B981)
    val StatusReadyBg = Color(0x2610B981)
    val StatusDelivered = Color(0xFF3B82F6)
    val StatusDeliveredBg = Color(0x263B82F6)
    val Error = Color(0xFFF87171)
    val ErrorBg = Color(0x26EF4444)
}

fun Modifier.liquidGlassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    prismRing: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier {
    val base = this
        .clip(shape)
        .background(KomandaTokens.LiquidGlassBrush, shape)
    return if (prismRing) {
        base.border(borderWidth, KomandaTokens.PrismRingBrush, shape)
    } else {
        base.border(borderWidth, KomandaTokens.Border, shape)
    }
}
