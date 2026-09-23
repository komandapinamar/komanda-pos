package com.komanda.business.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Standardized Komanda design tokens mirroring Komanda Web globals.css tokens.
 */
object KomandaTokens {
    // Surfaces & Backgrounds
    val Background = Color(0xFF09090B)       // Zinc950
    val Surface = Color(0xFF18181B)          // Zinc900
    val SurfaceVariant = Color(0xFF27272A)   // Zinc800
    val Border = Color(0xFF3F3F46)           // Zinc700
    val BorderSubtle = Color(0xFF27272A)     // Zinc800

    // Text & Content
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA1A1AA)    // Zinc400
    val TextMuted = Color(0xFF71717A)        // Zinc500

    // Design System Accents (mirrors Komanda web: --color-accent-tertiary, etc.)
    val AccentPrimary = Color(0xFF000000)
    val AccentSecondary = Color(0xFFFFFFFF)
    val AccentTertiary = Color(0xFFFFFFFF)   // Replaces amber for primary button/selection actions

    // Operational Status (Kitchen KDS & Despacho)
    val StatusPreparing = Color(0xFFF59E0B)
    val StatusPreparingBg = Color(0x33F59E0B)
    val StatusReady = Color(0xFF10B981)
    val StatusReadyBg = Color(0x3310B981)
    val StatusDelivered = Color(0xFF3B82F6)
    val StatusDeliveredBg = Color(0x333B82F6)
    val Error = Color(0xFFEF4444)
    val ErrorBg = Color(0x33EF4444)
}
