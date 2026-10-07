package com.komanda.business.features.orders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.OrderStatuses
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.features.billing.BillingService
import com.komanda.business.features.orders.CollectCashResult
import com.komanda.business.features.orders.OrderManager
import com.komanda.business.features.orders.RefundCashResult
import com.komanda.business.features.orders.SnapshotState
import com.komanda.business.hardware.printing.model.PrinterConfig
import com.komanda.business.hardware.printing.enums.PrinterRole
import com.komanda.business.ui.theme.Amber400
import com.komanda.business.ui.theme.Amber500
import com.komanda.business.ui.theme.Amber700
import com.komanda.business.ui.theme.Emerald600
import com.komanda.business.ui.theme.KomandaTokens
import com.komanda.business.ui.theme.Red400
import com.komanda.business.ui.theme.Zinc100
import com.komanda.business.ui.theme.Zinc300
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc600
import com.komanda.business.ui.theme.Zinc700
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950
import kotlinx.coroutines.launch

@Composable
fun OrdersDashboardScreen(
    orderManager: OrderManager,
    billingService: BillingService? = null,
    onNavigateToPos: () -> Unit,
    onNavigateToPrinterSettings: (() -> Unit)? = null,
    tenantName: String? = null,
    onLogout: (() -> Unit)? = null,
    onCollectCash: ((AdminDashboardOrder) -> Unit)? = null
) {
    val orders by orderManager.orders.collectAsState()
    val snapshotState by orderManager.snapshotState.collectAsState()
    val connectionState by orderManager.connectionState.collectAsState()
    val lastUpdatedAt by orderManager.lastUpdatedAt.collectAsState()
    val transitioningOrderId by orderManager.transitioningOrderId.collectAsState()
    val scope = rememberCoroutineScope()
    var orderToPrint by remember { mutableStateOf<AdminDashboardOrder?>(null) }
    var orderToCollectCash by remember { mutableStateOf<AdminDashboardOrder?>(null) }
    var orderToRefundCash by remember { mutableStateOf<AdminDashboardOrder?>(null) }
    var orderToVoidUnpaid by remember { mutableStateOf<AdminDashboardOrder?>(null) }
    val masterAutoPrint by orderManager.masterAutoPrintEnabled.collectAsState()
    val configuredPrinters by orderManager.configuredPrinters.collectAsState()

    Scaffold(
        containerColor = Zinc950
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // "OPERACIÓN" / "Pedidos en curso" / "Los cambios se reciben por eventos incrementales..."
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!tenantName.isNullOrBlank()) "Komanda POS - ${tenantName.uppercase()}" else "Komanda Business",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Los cambios se reciben por eventos incrementales del tenant activo.",
                            fontSize = 14.sp,
                            color = Zinc400
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Master Auto-Print Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Zinc900, RoundedCornerShape(2.dp))
                                .border(1.dp, Zinc800, RoundedCornerShape(2.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Impresión Automática",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (masterAutoPrint) KomandaTokens.AccentTertiary else Zinc400
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = masterAutoPrint,
                                onCheckedChange = { orderManager.setMasterAutoPrint(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Zinc950,
                                    checkedTrackColor = KomandaTokens.AccentTertiary,
                                    uncheckedThumbColor = Zinc400,
                                    uncheckedTrackColor = Zinc800
                                )
                            )
                        }

                        if (onNavigateToPrinterSettings != null) {
                            IconButton(onClick = onNavigateToPrinterSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Configurar Impresoras", tint = Zinc400)
                            }
                        }

                        IconButton(
                            onClick = { orderManager.refreshOrders() }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Zinc400)
                        }

                        if (onLogout != null) {
                            OutlinedButton(
                                onClick = onLogout,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Zinc400),
                                shape = RoundedCornerShape(2.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Salir",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToPos,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KomandaTokens.AccentTertiary,
                                contentColor = KomandaTokens.AccentPrimary
                            ),
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Crear pedido directo",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Main section container (AdminOrdersLive.tsx)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Zinc800))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Section Header: "Activos" / "{N} pedidos esperando entrega" + Connection badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Activos",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${orders.size} pedido${if (orders.size == 1) "" else "s"} esperando entrega.",
                                    fontSize = 14.sp,
                                    color = Zinc400
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ConnectionBadge(connectionState = connectionState)

                                if (lastUpdatedAt != null) {
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, Zinc700, CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Sync ${AdminDashboardOrder.formatDate(lastUpdatedAt)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Zinc400
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Zinc800)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Empty State or Orders List
                        if (snapshotState == SnapshotState.Error) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                                Text("No se pudieron actualizar los pedidos. Verificá la conexión.", color = Red400)
                                OutlinedButton(onClick = { orderManager.refreshOrders() }) { Text("Reintentar") }
                            }
                        }
                        if (orders.isEmpty() && snapshotState == SnapshotState.Loading) {
                            Text("Cargando pedidos...", color = Zinc300, modifier = Modifier.padding(vertical = 40.dp))
                        } else if (orders.isEmpty() && snapshotState == SnapshotState.Ready) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No hay pedidos en proceso.",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Cuando entre un nuevo pedido aprobado va a aparecer aca.",
                                    fontSize = 14.sp,
                                    color = Zinc400
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                orders.forEach { order ->
                                    AdminDashboardOrderCard(
                                        order = order,
                                        isTransitioning = transitioningOrderId == order.id,
                                        onTransition = { orderManager.transitionOrder(order) },
                                        onCollectCash = {
                                            if (onCollectCash != null) {
                                                onCollectCash(order)
                                            } else {
                                                orderToCollectCash = order
                                            }
                                        },
                                        onVoidUnpaid = { orderToVoidUnpaid = order },
                                        onRefundCash = { orderToRefundCash = order },
                                        onPrint = {
                                            orderToPrint = order
                                        },
                                        onInvoice = {
                                            billingService?.let { bs ->
                                                scope.launch {
                                                    // todo: por que seria demo?
                                                    bs.invoiceOrder("demo", order)
                                                }
                                            }
                                        },
                                        onCall = {
                                            orderManager.announceOrderReady(
                                                purchaseNumber = order.purchaseNumber,
                                                clientName = order.customer.name
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        orderToPrint?.let { order ->
            PrintTargetDialog(
                order = order,
                printers = configuredPrinters,
                onDismiss = { orderToPrint = null },
                onSelectPrinter = { targetId ->
                    scope.launch {
                        orderManager.printOrderTicket(order, targetPrinterId = targetId)
                    }
                    orderToPrint = null
                }
            )
        }

        orderToCollectCash?.let { order ->
            CollectCashDialog(
                order = order,
                orderManager = orderManager,
                onDismiss = { orderToCollectCash = null }
            )
        }

        orderToRefundCash?.let { order ->
            RefundCashDialog(
                order = order,
                orderManager = orderManager,
                onDismiss = { orderToRefundCash = null }
            )
        }

        orderToVoidUnpaid?.let { order ->
            VoidUnpaidOrderDialog(
                order = order,
                onDismiss = { orderToVoidUnpaid = null },
                onConfirm = {
                    scope.launch {
                        orderManager.voidUnpaidOrder(order, "unpaid_cash_voided_by_pos")
                    }
                    orderToVoidUnpaid = null
                }
            )
        }
    }
}

@Composable
fun ConnectionBadge(connectionState: ConnectionState) {
    val (label, bg, border, text) = when (connectionState) {
        ConnectionState.LIVE -> Quad(
            "Conexion En vivo",
            Emerald600.copy(alpha = 0.15f),
            Emerald600.copy(alpha = 0.4f),
            Color(0xFF34D399)
        )
        ConnectionState.RECONNECTING -> Quad(
            "Conexion Reconectando",
            KomandaTokens.StatusPreparingBg,
            KomandaTokens.StatusPreparing.copy(alpha = 0.4f),
            KomandaTokens.StatusPreparing
        )
        ConnectionState.CONNECTING -> Quad(
            "Conexion Conectando",
            Zinc700.copy(alpha = 0.2f),
            Zinc700.copy(alpha = 0.4f),
            Zinc300
        )
        ConnectionState.UNAUTHORIZED -> Quad(
            "Sesion no autorizada", Red400.copy(alpha = 0.15f), Red400, Red400
        )
    }

    Box(
        modifier = Modifier
            .background(bg, CircleShape)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.05.em,
            color = text
        )
    }
}

@Composable
fun AdminDashboardOrderCard(
    order: AdminDashboardOrder,
    isTransitioning: Boolean,
    onTransition: () -> Unit,
    onPrint: () -> Unit,
    onInvoice: () -> Unit,
    onCall: () -> Unit = {},
    onCollectCash: () -> Unit = {},
    onVoidUnpaid: () -> Unit = {},
    onRefundCash: () -> Unit = {}
) {
    val isCashPending = order.isCashPending
    val isCashPaid = order.paymentStatus == "paid" && order.tender == "cash"
    val cardBorderColor = if (isCashPending) Amber500 else Zinc800
    val cardBorderWidth = if (isCashPending) 1.5.dp else 1.dp
    val cardBgColor = if (isCashPending) Color(0xFF14120B) else Zinc950

    // Fuerza recomposición periódica para que el countdown de la orden
    // pendiente de efectivo no quede congelado.
    var countdownTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isCashPending) {
        if (!isCashPending) return@LaunchedEffect
        while (true) {
            countdownTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(cardBorderWidth, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Pills (Compra #N + Source + Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Compra #${order.purchaseNumber}",
                                color = Zinc950,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!order.pickupPin.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(KomandaTokens.StatusReadyBg, CircleShape)
                                    .border(1.dp, KomandaTokens.StatusReady, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "PIN: #${order.pickupPin}",
                                    color = KomandaTokens.StatusReady,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, if (isCashPending) Amber700 else Zinc700, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = AdminDashboardOrder.sourceLabel(order.source).uppercase(),
                                color = if (isCashPending) Amber400 else Zinc300,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.05.em
                            )
                        }

                        if (isCashPending) {
                            Box(
                                modifier = Modifier
                                    .background(Amber500.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, Amber500, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "PENDIENTE DE PAGO - EFECTIVO",
                                    color = Amber400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.05.em
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = order.customer.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aprobado: ${AdminDashboardOrder.formatDate(order.approvedAt)}",
                        fontSize = 13.sp,
                        color = Zinc400
                    )
                    Text(
                        text = "Creado: ${AdminDashboardOrder.formatDate(order.createdAt)}",
                        fontSize = 13.sp,
                        color = Zinc400
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isCashPending) {
                        Box(
                            modifier = Modifier
                                .background(Amber500.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.5.dp, Amber500, RoundedCornerShape(4.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "PENDIENTE DE PAGO - EFECTIVO",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.02).em,
                                color = Amber400
                            )
                        }

                        val nowMillis = if (countdownTick > 0L) countdownTick else System.currentTimeMillis()
                        val isExpired = order.isPaymentExpired(nowMillis)
                        val minutes = order.minutesRemaining(nowMillis)
                        val expirationText = when {
                            isExpired || (minutes != null && minutes <= 0L) -> "Tiempo de pago expirado"
                            minutes != null -> "Expira en $minutes min"
                            else -> null
                        }
                        if (expirationText != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = expirationText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExpired) Red400 else Amber400
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Total a cobrar: $${order.total} ${order.currency}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        val statusText = AdminDashboardOrder.statusLabel(order.status)
                        val isReady = order.status == OrderStatuses.READY
                        val statusBgColor = if (isReady) KomandaTokens.StatusReadyBg else KomandaTokens.StatusPreparingBg
                        val statusTextColor = if (isReady) KomandaTokens.StatusReady else KomandaTokens.StatusPreparing
                        val statusBorderColor = if (isReady) KomandaTokens.StatusReady else KomandaTokens.StatusPreparing

                        Box(
                            modifier = Modifier
                                .background(statusBgColor, RoundedCornerShape(4.dp))
                                .border(1.5.dp, statusBorderColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = statusText.uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.02).em,
                                color = statusTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (!order.paymentStatus.isNullOrBlank()) {
                        Text(
                            text = "Pago: ${order.paymentStatus}",
                            fontSize = 13.sp,
                            color = Zinc400
                        )
                    }
                    Text(
                        text = "Pedido interno: ${order.id}",
                        fontSize = 13.sp,
                        color = Zinc400
                    )
                }

                // Action buttons on right
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCashPending) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onCollectCash,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Amber500,
                                    contentColor = Zinc950
                                ),
                                shape = RoundedCornerShape(2.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "Cobrar en Efectivo",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onVoidUnpaid,
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                                border = BorderStroke(1.dp, Red400.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Anular Pedido Impago",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        val nextLabel = AdminDashboardOrder.nextStatusLabel(order.status)
                        if (nextLabel != null) {
                            val isNextReady = AdminDashboardOrder.nextStatus(order.status) == OrderStatuses.READY
                            val btnBgColor = if (isNextReady) KomandaTokens.StatusReady else KomandaTokens.AccentTertiary
                            val btnFgColor = if (isNextReady) Color.White else KomandaTokens.AccentPrimary
                            Button(
                                onClick = onTransition,
                                enabled = !isTransitioning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = btnBgColor,
                                    contentColor = btnFgColor
                                ),
                                shape = RoundedCornerShape(2.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = if (isTransitioning) "Actualizando..." else nextLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (isCashPaid) {
                            OutlinedButton(
                                onClick = onRefundCash,
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                                border = BorderStroke(1.dp, Red400.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Cancelar y Devolver Efectivo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onPrint,
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Ticket", tint = Zinc300, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ticket", fontSize = 12.sp, color = Zinc300)
                        }

                        OutlinedButton(
                            onClick = onInvoice,
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = "AFIP", tint = Zinc300, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AFIP", fontSize = 12.sp, color = Zinc300)
                        }

                        OutlinedButton(
                            onClick = onCall,
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VOZ", fontSize = 12.sp, color = Zinc300)
                        }
                    }
                }
            }

            // Notes Block (if present)
            if (!order.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Zinc900, RoundedCornerShape(2.dp))
                        .border(1.dp, Zinc800, RoundedCornerShape(2.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(text = "Notas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Zinc100)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = order.notes, fontSize = 13.sp, color = Zinc300)
                    }
                }
            }

            // Products Block (if lines present)
            if (order.lines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Zinc900, RoundedCornerShape(2.dp))
                        .border(1.dp, Zinc800, RoundedCornerShape(2.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(text = "Productos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Zinc100)
                        Spacer(modifier = Modifier.height(8.dp))

                        order.lines.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${line.quantity}x ${line.name}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Zinc100
                                    )
                                    if (!line.note.isNullOrBlank()) {
                                        Text(text = line.note, fontSize = 12.sp, color = Zinc400)
                                    }
                                    line.options.forEach { opt ->
                                        val delta = opt.priceDelta.toDoubleOrNull() ?: 0.0
                                        val deltaStr = if (delta > 0) " (+$$delta)" else ""
                                        Text(
                                            text = "+ ${opt.name}$deltaStr",
                                            fontSize = 12.sp,
                                            color = Zinc400,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "$${line.lineTotal}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Zinc300
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Zinc800)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Subtotal", fontSize = 13.sp, color = Zinc400)
                            Text(text = "$${order.subtotal}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Zinc300)
                        }

                        val discount = order.discountTotal.toDoubleOrNull() ?: 0.0
                        if (discount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Descuento", fontSize = 13.sp, color = Red400)
                                Text(text = "-$${order.discountTotal}", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Red400)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Total", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                text = "$${order.total} ${order.currency}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoidUnpaidOrderDialog(
    order: AdminDashboardOrder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!confirmed) onDismiss() },
        title = {
            Text(
                text = "Anular Pedido Impago — Compra #${order.purchaseNumber}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Text(
                text = "¿Seguro que querés anular este pedido impago? La orden se cancelará y no se registrará ningún movimiento de caja.",
                fontSize = 14.sp,
                color = Zinc300
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (confirmed) return@Button
                    confirmed = true
                    onConfirm()
                },
                enabled = !confirmed,
                colors = ButtonDefaults.buttonColors(containerColor = Red400, contentColor = Color.White),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("Sí, anular", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !confirmed) {
                Text("No, mantener", color = Zinc400)
            }
        },
        containerColor = Zinc900
    )
}

@Composable
private fun PrintTargetDialog(
    order: AdminDashboardOrder,
    printers: List<PrinterConfig>,
    onDismiss: () -> Unit,
    onSelectPrinter: (targetPrinterId: String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Imprimir Comanda — Compra #${order.purchaseNumber}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Seleccioná la impresora de destino:",
                    fontSize = 13.sp,
                    color = Zinc400
                )

                val activePrinters = printers.filter { it.role != PrinterRole.DISABLED }
                if (activePrinters.isEmpty()) {
                    Text(
                        text = "No hay impresoras activas configuradas.",
                        fontSize = 13.sp,
                        color = Red400
                    )
                } else {
                    activePrinters.forEach { p ->
                        val roleLabel = when (p.role) {
                            PrinterRole.KITCHEN -> "Cocina"
                            PrinterRole.COUNTER -> "Mostrador"
                            PrinterRole.DISABLED -> "Desactivada"
                        }
                        OutlinedButton(
                            onClick = { onSelectPrinter(p.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("[$roleLabel]", fontSize = 12.sp, color = KomandaTokens.AccentTertiary)
                            }
                        }
                    }

                    if (activePrinters.size > 1) {
                        Button(
                            onClick = { onSelectPrinter("ALL") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KomandaTokens.AccentTertiary, contentColor = KomandaTokens.AccentPrimary)
                        ) {
                            Text("Todas las impresoras activas", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Zinc400)
            }
        },
        containerColor = Zinc900
    )
}

@Composable
fun CollectCashDialog(
    order: AdminDashboardOrder,
    orderManager: OrderManager,
    onDismiss: () -> Unit
) {
    var authMethod by remember { mutableStateOf("pickup_pin") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Cobrar en Efectivo — Compra #${order.purchaseNumber}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Prominent Total Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Zinc950, RoundedCornerShape(4.dp))
                        .border(1.dp, Amber500.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total a cobrar en efectivo:",
                            fontSize = 13.sp,
                            color = Zinc400
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${order.total} ${order.currency}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Amber400,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = "Seleccioná el método de validación:",
                    fontSize = 13.sp,
                    color = Zinc400
                )

                // Selector: PIN del Pedido vs Confirmar con mi Cuenta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPin = authMethod == "pickup_pin"
                    val isAccount = authMethod == "account_auth"

                    OutlinedButton(
                        onClick = {
                            authMethod = "pickup_pin"
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPin) Amber500.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isPin) Amber400 else Zinc400
                        ),
                        border = BorderStroke(1.dp, if (isPin) Amber500 else Zinc800),
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "PIN del Pedido",
                            fontSize = 13.sp,
                            fontWeight = if (isPin) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            authMethod = "account_auth"
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isAccount) Amber500.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isAccount) Amber400 else Zinc400
                        ),
                        border = BorderStroke(1.dp, if (isAccount) Amber500 else Zinc800),
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Confirmar con mi Cuenta",
                            fontSize = 13.sp,
                            fontWeight = if (isAccount) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Input / Details based on selected method
                if (authMethod == "pickup_pin") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { it.isDigit() }) {
                                    pin = input
                                    errorMessage = null
                                }
                            },
                            label = { Text("PIN de 4 dígitos") },
                            placeholder = { Text("Ej: 1234", color = Zinc600) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = Zinc800,
                                focusedLabelColor = Amber400,
                                unfocusedLabelColor = Zinc400,
                                cursorColor = Amber400,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )
                        Text(
                            text = "Ingresá el PIN numérico de 4 dígitos provisto por el comensal.",
                            fontSize = 11.sp,
                            color = Zinc400
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Zinc950, RoundedCornerShape(2.dp))
                            .border(1.dp, Zinc800, RoundedCornerShape(2.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Se autorizará el cobro en efectivo utilizando la cuenta de usuario del operador actualmente autenticado.",
                            fontSize = 13.sp,
                            color = Zinc300
                        )
                    }
                }

                // Error message banner (if any)
                if (!errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Red400.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                            .border(1.dp, Red400.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Red400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canConfirm = !isLoading && (
                (authMethod == "pickup_pin" && pin.length == 4) ||
                authMethod == "account_auth"
            )

            Button(
                onClick = {
                    if (!canConfirm) return@Button
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val code = if (authMethod == "pickup_pin") pin else null
                        val result = orderManager.collectCash(order, authMethod, code)
                        isLoading = false
                        when (result) {
                            is CollectCashResult.Success -> {
                                onDismiss()
                            }
                            is CollectCashResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = Zinc950,
                    disabledContainerColor = Zinc800,
                    disabledContentColor = Zinc600
                ),
                shape = RoundedCornerShape(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isLoading) "Procesando cobro..." else "Confirmar Cobro",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar", color = Zinc400)
            }
        },
        containerColor = Zinc900
    )
}

@Composable
fun RefundCashDialog(
    order: AdminDashboardOrder,
    orderManager: OrderManager,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var operatorNote by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Cancelar y Devolver Efectivo — Compra #${order.purchaseNumber}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Zinc950, RoundedCornerShape(4.dp))
                        .border(1.dp, Red400.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Devolver en efectivo al cliente: $${formatRefundAmount(order.total)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Red400,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        errorMessage = null
                    },
                    label = { Text("Motivo de cancelación") },
                    placeholder = { Text("Ej: Sin stock, error operativo, reclamo", color = Zinc600) },
                    singleLine = false,
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Red400,
                        unfocusedBorderColor = Zinc800,
                        focusedLabelColor = Red400,
                        unfocusedLabelColor = Zinc400,
                        cursorColor = Red400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = operatorNote,
                    onValueChange = { operatorNote = it },
                    label = { Text("Nota del operador (opcional)") },
                    singleLine = false,
                    minLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Zinc400,
                        unfocusedBorderColor = Zinc800,
                        focusedLabelColor = Zinc400,
                        unfocusedLabelColor = Zinc400,
                        cursorColor = Zinc400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                if (!errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Red400.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                            .border(1.dp, Red400.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Red400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canConfirm = !isLoading && reason.trim().isNotEmpty()

            Button(
                onClick = {
                    if (!canConfirm) return@Button
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val result = orderManager.refundCash(
                            order,
                            reason.trim(),
                            operatorNote.trim().ifEmpty { null }
                        )
                        isLoading = false
                        when (result) {
                            is RefundCashResult.Success -> onDismiss()
                            is RefundCashResult.Error -> errorMessage = result.message
                        }
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Red400,
                    contentColor = Color.White,
                    disabledContainerColor = Zinc800,
                    disabledContentColor = Zinc600
                ),
                shape = RoundedCornerShape(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isLoading) "Procesando devolución..." else "Confirmar Devolución",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar", color = Zinc400)
            }
        },
        containerColor = Zinc900
    )
}

private fun formatRefundAmount(total: String): String {
    val value = total.toDoubleOrNull() ?: return total
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("es", "AR"))
    val df = java.text.DecimalFormat("#,##0.##", symbols)
    return df.format(value)
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
