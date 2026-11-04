package com.komanda.business.features.projector

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class ProjectorShiftMode(val label: String, val badgeText: String, val spotlightText: String) {
    AUTO(
        label = "Automático (01–08 hs)",
        badgeText = "Horario Automático",
        spotlightText = ""
    ),
    NIGHT_START(
        label = "01:00–03:30 Apertura",
        badgeText = "01:00–03:30 · Apertura Nocturna",
        spotlightText = "Destacado del turno: Pollo Sisu · Pizzas · Quesadillas"
    ),
    BAJON_MODE(
        label = "03:30–05:30 Bajón",
        badgeText = "03:30–05:30 · Modo Bajón",
        spotlightText = "Destacado del turno: Pollo Sisu + Salsas + Papas"
    ),
    SUNRISE_SPECIAL(
        label = "05:30–08:00 Sunrise",
        badgeText = "05:30–08:00 · Sunrise Special",
        spotlightText = "Recién horneado: Medialunas J&Q calientes + Cookies"
    );

    fun resolveForHour(hourOfDay: Int, minute: Int): ProjectorShiftMode {
        if (this != AUTO) return this
        val totalMinutes = hourOfDay * 60 + minute
        return when {
            totalMinutes in (1 * 60)..<((3 * 60) + 30) -> NIGHT_START
            totalMinutes in ((3 * 60) + 30)..<((5 * 60) + 30) -> BAJON_MODE
            totalMinutes in ((5 * 60) + 30)..<(9 * 60) -> SUNRISE_SPECIAL
            else -> BAJON_MODE
        }
    }
}

/**
 * Normalized 2D point in [0f..1f] relative to the projector output viewport.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamped(): NormalizedPoint = NormalizedPoint(
        x = x.coerceIn(0.02f, 0.98f),
        y = y.coerceIn(0.02f, 0.98f)
    )
}

data class ProjectorCalibrationConfig(
    val topLeft: NormalizedPoint = NormalizedPoint(0.14f, 0.22f),
    val topRight: NormalizedPoint = NormalizedPoint(0.88f, 0.08f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.95f, 0.90f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.12f, 0.90f),
    val verticalStretch: Float = 1.15f,
    val shiftMode: ProjectorShiftMode = ProjectorShiftMode.AUTO,
    val autoProjectOnExternalDisplay: Boolean = true,
    val showFloorGridInCalibration: Boolean = true,
    val batchFlashUntilMillis: Long = 0L,
    val batchFlashHeadline: String = "Recién salido del horno:\nMedialunas J&Q + Cookies"
) {
    val corners: List<NormalizedPoint>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withCorner(index: Int, point: NormalizedPoint): ProjectorCalibrationConfig {
        val safe = point.clamped()
        return when (index) {
            0 -> copy(topLeft = safe)
            1 -> copy(topRight = safe)
            2 -> copy(bottomRight = safe)
            3 -> copy(bottomLeft = safe)
            else -> this
        }
    }

    fun rotated180(): ProjectorCalibrationConfig = copy(
        topLeft = bottomRight,
        topRight = bottomLeft,
        bottomRight = topLeft,
        bottomLeft = topRight
    )

    companion object {
        fun presetFlat(): ProjectorCalibrationConfig = ProjectorCalibrationConfig(
            topLeft = NormalizedPoint(0.06f, 0.06f),
            topRight = NormalizedPoint(0.94f, 0.06f),
            bottomRight = NormalizedPoint(0.94f, 0.94f),
            bottomLeft = NormalizedPoint(0.06f, 0.94f),
            verticalStretch = 1.0f
        )

        fun presetFloor45(): ProjectorCalibrationConfig = ProjectorCalibrationConfig(
            topLeft = NormalizedPoint(0.14f, 0.22f),
            topRight = NormalizedPoint(0.88f, 0.08f),
            bottomRight = NormalizedPoint(0.95f, 0.90f),
            bottomLeft = NormalizedPoint(0.12f, 0.90f),
            verticalStretch = 1.15f
        )

        fun presetCornerDiagonal(): ProjectorCalibrationConfig = ProjectorCalibrationConfig(
            topLeft = NormalizedPoint(0.28f, 0.06f),
            topRight = NormalizedPoint(0.93f, 0.16f),
            bottomRight = NormalizedPoint(0.86f, 0.92f),
            bottomLeft = NormalizedPoint(0.10f, 0.80f),
            verticalStretch = 1.20f
        )
    }
}

class ProjectorConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadFromPrefs())
    val config: StateFlow<ProjectorCalibrationConfig> = _config.asStateFlow()

    fun update(transform: (ProjectorCalibrationConfig) -> ProjectorCalibrationConfig) {
        val next = transform(_config.value)
        _config.value = next
        saveToPrefs(next)
    }

    fun applyPreset(preset: String) {
        update { current ->
            val base = when (preset) {
                "flat" -> ProjectorCalibrationConfig.presetFlat()
                "corner" -> ProjectorCalibrationConfig.presetCornerDiagonal()
                else -> ProjectorCalibrationConfig.presetFloor45()
            }
            current.copy(
                topLeft = base.topLeft,
                topRight = base.topRight,
                bottomRight = base.bottomRight,
                bottomLeft = base.bottomLeft,
                verticalStretch = base.verticalStretch
            )
        }
    }

    fun rotate180() {
        update { it.rotated180() }
    }

    fun updateCorner(index: Int, point: NormalizedPoint) {
        update { it.withCorner(index, point) }
    }

    fun updateVerticalStretch(stretch: Float) {
        update { it.copy(verticalStretch = stretch.coerceIn(0.90f, 1.60f)) }
    }

    fun setShiftMode(mode: ProjectorShiftMode) {
        update { it.copy(shiftMode = mode) }
    }

    fun setAutoProject(enabled: Boolean) {
        update { it.copy(autoProjectOnExternalDisplay = enabled) }
    }

    fun setShowFloorGrid(enabled: Boolean) {
        update { it.copy(showFloorGridInCalibration = enabled) }
    }

    fun triggerBatchFlash(durationMillis: Long = 10_000L, headline: String? = null) {
        val until = System.currentTimeMillis() + durationMillis
        update {
            it.copy(
                batchFlashUntilMillis = until,
                batchFlashHeadline = headline ?: it.batchFlashHeadline
            )
        }
    }

    fun currentResolvedShift(): ProjectorShiftMode {
        val cal = Calendar.getInstance()
        return _config.value.shiftMode.resolveForHour(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    private fun loadFromPrefs(): ProjectorCalibrationConfig {
        val default = ProjectorCalibrationConfig.presetFloor45()
        val shiftName = prefs.getString("shift_mode", ProjectorShiftMode.AUTO.name)
        val shift = runCatching { ProjectorShiftMode.valueOf(shiftName ?: "") }
            .getOrDefault(ProjectorShiftMode.AUTO)

        return ProjectorCalibrationConfig(
            topLeft = NormalizedPoint(
                prefs.getFloat("tl_x", default.topLeft.x),
                prefs.getFloat("tl_y", default.topLeft.y)
            ).clamped(),
            topRight = NormalizedPoint(
                prefs.getFloat("tr_x", default.topRight.x),
                prefs.getFloat("tr_y", default.topRight.y)
            ).clamped(),
            bottomRight = NormalizedPoint(
                prefs.getFloat("br_x", default.bottomRight.x),
                prefs.getFloat("br_y", default.bottomRight.y)
            ).clamped(),
            bottomLeft = NormalizedPoint(
                prefs.getFloat("bl_x", default.bottomLeft.x),
                prefs.getFloat("bl_y", default.bottomLeft.y)
            ).clamped(),
            verticalStretch = prefs.getFloat("vertical_stretch", default.verticalStretch)
                .coerceIn(0.90f, 1.60f),
            shiftMode = shift,
            autoProjectOnExternalDisplay = prefs.getBoolean("auto_project", true),
            showFloorGridInCalibration = prefs.getBoolean("show_floor_grid", true)
        )
    }

    private fun saveToPrefs(cfg: ProjectorCalibrationConfig) {
        prefs.edit()
            .putFloat("tl_x", cfg.topLeft.x)
            .putFloat("tl_y", cfg.topLeft.y)
            .putFloat("tr_x", cfg.topRight.x)
            .putFloat("tr_y", cfg.topRight.y)
            .putFloat("br_x", cfg.bottomRight.x)
            .putFloat("br_y", cfg.bottomRight.y)
            .putFloat("bl_x", cfg.bottomLeft.x)
            .putFloat("bl_y", cfg.bottomLeft.y)
            .putFloat("vertical_stretch", cfg.verticalStretch)
            .putString("shift_mode", cfg.shiftMode.name)
            .putBoolean("auto_project", cfg.autoProjectOnExternalDisplay)
            .putBoolean("show_floor_grid", cfg.showFloorGridInCalibration)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "komanda_projector_prefs"
    }
}
