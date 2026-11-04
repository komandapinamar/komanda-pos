package com.komanda.business.features.projector

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.komanda.business.R
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.features.orders.OrderManager
import com.komanda.business.features.projector.ui.ProjectorFloorCanvas
import com.komanda.business.ui.theme.KomandaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int
)

/**
 * Manages secondary displays connected to the Telpo:
 * 1. The Telpo's built-in rear customer display ("Pantalla HDMI") automatically and permanently
 *    shows the Komanda brand artwork ([R.drawable.rear_screen_komanda]).
 * 2. Any wireless/external projector (such as the Home Cinema DUB2800 / DUB3800) automatically
 *    shows [FloorProjectorPresentation] rendering [ProjectorFloorCanvas].
 */
class ProjectorDisplayController(
    private val activity: ComponentActivity,
    private val configStore: ProjectorConfigStore
) {
    private val tag = "ProjectorDisplayCtrl"
    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _connectedDisplays = MutableStateFlow<List<ExternalDisplayInfo>>(emptyList())
    val connectedDisplays: StateFlow<List<ExternalDisplayInfo>> = _connectedDisplays.asStateFlow()

    private val _isPresentingExternal = MutableStateFlow(false)
    val isPresentingExternal: StateFlow<Boolean> = _isPresentingExternal.asStateFlow()

    private var rearPresentation: RearCustomerPresentation? = null
    private var activePresentation: FloorProjectorPresentation? = null

    private val orderManagerFlow = MutableStateFlow<OrderManager?>(null)
    private val tenantNameFlow = MutableStateFlow<String?>(null)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.i(tag, "External display added: id=$displayId")
            refreshDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.i(tag, "External display removed: id=$displayId")
            if (rearPresentation?.display?.displayId == displayId) {
                dismissRearPresentation()
            }
            if (activePresentation?.display?.displayId == displayId) {
                dismissPresentation()
            }
            refreshDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
        }
    }

    fun start() {
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshDisplays()
    }

    fun stop() {
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        dismissRearPresentation()
        dismissPresentation()
    }

    fun bindOrderManager(orderManager: OrderManager?, tenantName: String?) {
        orderManagerFlow.value = orderManager
        tenantNameFlow.value = tenantName
        refreshDisplays()
    }

    fun refreshDisplays() {
        val presentationDisplays = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .toList()
        val allSecondaryDisplays = displayManager.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }

        val secondaryDisplays = (presentationDisplays + allSecondaryDisplays)
            .distinctBy { it.displayId }

        // 1. Rear customer screen on the back of the Telpo ("Pantalla HDMI")
        val rearDisplay = secondaryDisplays.firstOrNull { isRearCustomerDisplay(it) }
        if (rearDisplay != null) {
            if (rearPresentation?.display?.displayId != rearDisplay.displayId ||
                rearPresentation?.isShowing != true
            ) {
                showRearOnDisplay(rearDisplay)
            }
        } else {
            dismissRearPresentation()
        }

        // 2. External / wireless floor projector displays (DUB2800 / DUB3800 / Cast)
        val projectorDisplays = secondaryDisplays
            .filterNot { isRearCustomerDisplay(it) }
            .sortedWith(
                compareBy<Display> { displayPriorityRank(it) }
                    .thenByDescending { displayPixelArea(it) }
                    .thenByDescending { it.displayId }
            )

        _connectedDisplays.value = projectorDisplays.map { display ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            ExternalDisplayInfo(
                displayId = display.displayId,
                name = display.name ?: "Proyector #${display.displayId}",
                width = metrics.widthPixels,
                height = metrics.heightPixels
            )
        }

        val firstProjector = projectorDisplays.firstOrNull()
        val shouldPresentProjector = configStore.config.value.autoProjectOnExternalDisplay &&
            firstProjector != null

        if (shouldPresentProjector && firstProjector != null) {
            if (activePresentation?.display?.displayId != firstProjector.displayId ||
                activePresentation?.isShowing != true
            ) {
                showOnDisplay(firstProjector)
            }
        } else {
            dismissPresentation()
        }
    }

    private fun showRearOnDisplay(display: Display) {
        dismissRearPresentation()
        runCatching {
            val presentation = RearCustomerPresentation(
                activity = activity,
                display = display
            )
            presentation.show()
            rearPresentation = presentation
            Log.i(tag, "Rear customer Presentation active on '${display.name}' (${display.displayId})")
        }.onFailure { err ->
            Log.w(tag, "Failed to show Rear Customer Presentation on '${display.name}'", err)
        }
    }

    private fun dismissRearPresentation() {
        runCatching { rearPresentation?.dismiss() }
        rearPresentation = null
    }

    private fun showOnDisplay(display: Display) {
        dismissPresentation()
        runCatching {
            val presentation = FloorProjectorPresentation(
                activity = activity,
                display = display,
                orderManagerFlow = orderManagerFlow,
                tenantNameFlow = tenantNameFlow,
                configStore = configStore
            )
            presentation.show()
            activePresentation = presentation
            _isPresentingExternal.value = true
            Log.i(tag, "Projector Presentation active on display '${display.name}' (${display.displayId})")
        }.onFailure { err ->
            Log.w(tag, "Failed to show Presentation on external display", err)
            _isPresentingExternal.value = false
        }
    }

    fun dismissPresentation() {
        runCatching { activePresentation?.dismiss() }
        activePresentation = null
        _isPresentingExternal.value = false
    }

    private fun isRearCustomerDisplay(display: Display): Boolean {
        val name = (display.name ?: "").lowercase()
        val isExplicitProjector = name.contains("dub") ||
            name.contains("projector") ||
            name.contains("proyector") ||
            name.contains("wireless") ||
            name.contains("wifi") ||
            name.contains("miracast") ||
            name.contains("cast")
        if (isExplicitProjector) return false
        return name.contains("pantalla hdmi") ||
            name.contains("built-in") ||
            name.contains("customer") ||
            name.contains("sub")
    }

    private fun displayPriorityRank(display: Display): Int {
        val name = (display.name ?: "").lowercase()
        return when {
            name.contains("dub") ||
                name.contains("projector") ||
                name.contains("proyector") ||
                name.contains("wireless") ||
                name.contains("wifi") ||
                name.contains("miracast") ||
                name.contains("cast") -> 0
            else -> 1
        }
    }

    private fun displayPixelArea(display: Display): Int {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels * metrics.heightPixels
    }

    /**
     * Opens Android's native Wireless Display / Miracast / Cast settings on the Telpo
     * so the operator can pair with the Home Cinema DUB2800 / DUB3800 without leaving Komanda.
     */
    fun openWirelessDisplaySettings() {
        val actions = listOf(
            Settings.ACTION_CAST_SETTINGS,
            "android.settings.WIFI_DISPLAY_SETTINGS",
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                activity.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return
        }
    }
}

/**
 * Fullscreen static artwork presentation shown on the Telpo's rear customer screen ("Pantalla HDMI").
 */
private class RearCustomerPresentation(
    activity: ComponentActivity,
    display: Display
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(AndroidColor.BLACK)
            setImageResource(R.drawable.rear_screen_komanda)
        }
        setContentView(imageView)
    }
}

private class FloorProjectorPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val orderManagerFlow: StateFlow<OrderManager?>,
    private val tenantNameFlow: StateFlow<String?>,
    private val configStore: ProjectorConfigStore
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                KomandaTheme {
                    val orderManager by orderManagerFlow.collectAsState()
                    val tenantName by tenantNameFlow.collectAsState()
                    val orders: List<AdminDashboardOrder> = if (orderManager != null) {
                        val list by orderManager!!.orders.collectAsState()
                        list
                    } else {
                        emptyList()
                    }
                    val connectionState: ConnectionState = if (orderManager != null) {
                        val state by orderManager!!.connectionState.collectAsState()
                        state
                    } else {
                        ConnectionState.LIVE
                    }
                    val config by configStore.config.collectAsState()

                    ProjectorFloorCanvas(
                        orders = orders,
                        connectionState = connectionState,
                        config = config,
                        tenantName = tenantName,
                        showCalibrationGuides = false
                    )
                }
            }
        }
        setContentView(composeView)
    }
}
