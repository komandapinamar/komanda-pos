package com.komanda.business

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.komanda.business.core.audio.OrderAnnouncer
import com.komanda.business.core.auth.AuthManager
import com.komanda.business.core.auth.AuthState
import com.komanda.business.core.auth.SecureSessionStorage
import com.komanda.business.core.auth.ui.LoginScreen
import com.komanda.business.core.auth.ui.NoActiveTenantScreen
import com.komanda.business.core.auth.ui.TenantSelectionScreen
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.NetworkClient
import com.komanda.business.core.network.SharedPreferencesSseCursorStorage
import com.komanda.business.core.network.SseOrderEventListener
import com.komanda.business.features.billing.BillingService
import com.komanda.business.features.orders.OrderManager
import com.komanda.business.features.orders.ui.OrdersDashboardScreen
import com.komanda.business.features.pos.CheckoutAttemptStore
import com.komanda.business.features.pos.PosManager
import com.komanda.business.features.pos.ui.PosScreen
import com.komanda.business.features.settings.ui.PrinterSettingsScreen
import com.komanda.business.hardware.printing.PrinterRouter
import com.komanda.business.hardware.printing.drivers.UsbEscPosDriver
import com.komanda.business.ui.theme.Amber400
import com.komanda.business.ui.theme.KomandaTheme
import com.komanda.business.ui.theme.Zinc950
import kotlinx.coroutines.launch

enum class Screen {
    ORDERS_DASHBOARD,
    TAKE_ORDER,
    PRINTER_SETTINGS
}

private const val DEFAULT_BASE_URL = "https://throwing-dust-public.ngrok-free.dev"

class MainActivity : ComponentActivity() {

    private lateinit var announcer: OrderAnnouncer
    private lateinit var printerRouter: PrinterRouter
    private lateinit var usbDriver: UsbEscPosDriver
    private lateinit var authManager: AuthManager
    private lateinit var api: KomandaApi

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (UsbEscPosDriver.ACTION_USB_PERMISSION == intent?.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                Log.i("MainActivity", "USB permission broadcast received for ${device?.productName ?: device?.deviceName}: granted=$granted")
            }
        }
    }

    private var activeOrderManager: OrderManager? = null
    private var activePosManager: PosManager? = null
    private var activeBillingService: BillingService? = null

    private var currentBaseUrl by mutableStateOf(DEFAULT_BASE_URL)

    private fun updateServerUrl(newUrl: String) {
        val trimmed = newUrl.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed == currentBaseUrl) return
        currentBaseUrl = trimmed
        getSharedPreferences("komanda_pos_prefs", MODE_PRIVATE)
            .edit()
            .putString("server_base_url", trimmed)
            .apply()

        api = NetworkClient.create(
            baseUrl = trimmed,
            tokenProvider = { authManager.currentToken }
        )
        val sessionStorage = SecureSessionStorage(this)
        authManager = AuthManager(
            api = api,
            sessionStorage = sessionStorage
        )
        lifecycleScope.launch {
            authManager.initialize()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("komanda_pos_prefs", MODE_PRIVATE)
        val savedBaseUrl = prefs.getString("server_base_url", null)
        currentBaseUrl = if (savedBaseUrl.isNullOrBlank() || savedBaseUrl == "http://127.0.0.1:3000") {
            DEFAULT_BASE_URL
        } else {
            savedBaseUrl
        }

        // Register USB permission broadcast receiver
        val filter = IntentFilter(UsbEscPosDriver.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        // 1. Audio Announcer (Native Android TextToSpeech offline for speaker)
        announcer = OrderAnnouncer(this)

        // 2. Printing Subsystem (Multi-printer router with DantSu engine)
        printerRouter = PrinterRouter(this)
        usbDriver = UsbEscPosDriver(this)
        // Request USB permission on startup if a USB printer is connected
        usbDriver.requestPermission()

        // 3. Network API configured with dynamic bearer token provider
        api = NetworkClient.create(
            baseUrl = currentBaseUrl,
            tokenProvider = { authManager.currentToken }
        )

        // 4. Secure Auth Manager (AES256-GCM Keystore-backed)
        val sessionStorage = SecureSessionStorage(this)
        authManager = AuthManager(
            api = api,
            sessionStorage = sessionStorage
        )

        // 5. Initialize persisted session check
        lifecycleScope.launch {
            authManager.initialize()
        }

        setContent {
            KomandaTheme {
                val authState by authManager.authState.collectAsStateWithLifecycle()
                var currentScreen by remember { mutableStateOf(Screen.ORDERS_DASHBOARD) }

                when (val state = authState) {
                    is AuthState.Initializing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Zinc950),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Amber400)
                        }
                    }
                    is AuthState.LoggedOut, is AuthState.Loading, is AuthState.Error -> {
                        // Teardown any previously active managers
                        activeOrderManager?.stopListening()
                        activeOrderManager = null
                        activePosManager = null
                        activeBillingService = null

                        LoginScreen(
                            isLoading = state is AuthState.Loading,
                            errorMessage = (state as? AuthState.Error)?.message,
                            serverUrl = currentBaseUrl,
                            onServerUrlChanged = { updateServerUrl(it) },
                            onLogin = { email, password ->
                                lifecycleScope.launch {
                                    authManager.login(email, password)
                                }
                            }
                        )
                    }
                    is AuthState.SelectTenant -> {
                        activeOrderManager?.stopListening()
                        activeOrderManager = null
                        activePosManager = null
                        activeBillingService = null

                        TenantSelectionScreen(
                            tenants = state.tenants,
                            onSelectTenant = { tenantId ->
                                lifecycleScope.launch {
                                    authManager.selectTenant(
                                        token = state.token,
                                        expiresAt = state.expiresAt,
                                        tenantId = tenantId,
                                        availableTenants = state.tenants
                                    )
                                }
                            },
                            onLogout = {
                                lifecycleScope.launch {
                                    authManager.logout()
                                }
                            }
                        )
                    }
                    is AuthState.NoActiveTenant -> {
                        activeOrderManager?.stopListening()
                        activeOrderManager = null
                        activePosManager = null
                        activeBillingService = null

                        NoActiveTenantScreen(
                            onLogout = {
                                lifecycleScope.launch {
                                    authManager.logout()
                                }
                            }
                        )
                    }
                    is AuthState.Authenticated -> {
                        val session = state.session

                        val orderMgr = remember(session.tenantId, session.token, currentBaseUrl) {
                            val sseListener = SseOrderEventListener(
                                baseUrl = currentBaseUrl,
                                tenantId = session.tenantId,
                                authToken = session.token,
                                cursorStorage = SharedPreferencesSseCursorStorage(this@MainActivity)
                            )
                            OrderManager(
                                tenantId = session.tenantId,
                                api = api,
                                sseListener = sseListener,
                                announcer = announcer,
                                printerRouter = printerRouter,
                                tenantName = session.tenantName,
                                baseUrl = currentBaseUrl
                            ).also {
                                activeOrderManager?.stopListening()
                                activeOrderManager = it
                            }
                        }

                        DisposableEffect(orderMgr) {
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_START -> orderMgr.startListening()
                                    Lifecycle.Event.ON_STOP -> orderMgr.stopListening()
                                    else -> Unit
                                }
                            }
                            lifecycle.addObserver(observer)
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) orderMgr.startListening()
                            onDispose {
                                lifecycle.removeObserver(observer)
                                orderMgr.stopListening()
                            }
                        }

                        val attemptStore = remember { CheckoutAttemptStore(this@MainActivity) }

                        val posMgr = remember(session.tenantId, currentBaseUrl) {
                            PosManager(
                                tenantId = session.tenantId,
                                api = api,
                                printerRouter = printerRouter,
                                tenantName = session.tenantName,
                                baseUrl = currentBaseUrl,
                                attemptStore = attemptStore
                            ).also { activePosManager = it }
                        }

                        val billingSvc = remember(session.tenantId, currentBaseUrl) {
                            BillingService(
                                api = api,
                                printerRouter = printerRouter,
                                tenantName = session.tenantName,
                                baseUrl = currentBaseUrl
                            ).also { activeBillingService = it }
                        }

                        when (currentScreen) {
                            Screen.ORDERS_DASHBOARD -> {
                                OrdersDashboardScreen(
                                    orderManager = orderMgr,
                                    billingService = billingSvc,
                                    tenantName = session.tenantName.ifBlank { null },
                                    onNavigateToPos = { currentScreen = Screen.TAKE_ORDER },
                                    onNavigateToPrinterSettings = { currentScreen = Screen.PRINTER_SETTINGS },
                                    onLogout = {
                                        lifecycleScope.launch {
                                            authManager.logout()
                                        }
                                    }
                                )
                            }
                            Screen.TAKE_ORDER -> {
                                PosScreen(
                                    posManager = posMgr,
                                    onOrderCreated = {
                                        currentScreen = Screen.ORDERS_DASHBOARD
                                        orderMgr.refreshOrders()
                                    },
                                    onBack = { currentScreen = Screen.ORDERS_DASHBOARD }
                                )
                            }
                            Screen.PRINTER_SETTINGS -> {
                                PrinterSettingsScreen(
                                    router = printerRouter,
                                    onBack = { currentScreen = Screen.ORDERS_DASHBOARD }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        announcer.shutdown()
        activeOrderManager?.stopListening()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
