package com.komanda.business.features.orders.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.features.billing.BillingService
import com.komanda.business.features.orders.OrderManager
import com.komanda.business.ui.theme.Amber400
import com.komanda.business.ui.theme.Amber600
import com.komanda.business.ui.theme.Emerald600
import com.komanda.business.ui.theme.Red400
import com.komanda.business.ui.theme.Zinc100
import com.komanda.business.ui.theme.Zinc300
import com.komanda.business.ui.theme.Zinc400
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
    tenantName: String? = null,
    onLogout: (() -> Unit)? = null
) {
    val orders by orderManager.orders.collectAsState()
    val connectionState by orderManager.connectionState.collectAsState()
    val lastUpdatedAt by orderManager.lastUpdatedAt.collectAsState()
    val transitioningOrderId by orderManager.transitioningOrderId.collectAsState()
    val scope = rememberCoroutineScope()

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
            // Header identical to Next.js page.tsx:
            // "OPERACIÓN" / "Pedidos en curso" / "Los cambios se reciben por eventos incrementales..."
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!tenantName.isNullOrBlank()) "OPERACIÓN — ${tenantName.uppercase()}" else "OPERACIÓN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Amber400
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pedidos en curso",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
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
                                containerColor = Amber400,
                                contentColor = Zinc950
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
                        if (orders.isEmpty()) {
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
                                        onPrint = {
                                            scope.launch {
                                                orderManager.printOrderTicket(order)
                                            }
                                        },
                                        onInvoice = {
                                            billingService?.let { bs ->
                                                scope.launch {
                                                    bs.invoiceOrder("demo", order)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
            Amber600.copy(alpha = 0.15f),
            Amber600.copy(alpha = 0.4f),
            Amber400
        )
        ConnectionState.CONNECTING -> Quad(
            "Conexion Conectando",
            Zinc700.copy(alpha = 0.2f),
            Zinc700.copy(alpha = 0.4f),
            Zinc300
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
            letterSpacing = 1.sp,
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
    onInvoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc950),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Zinc800))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Pills (Compra #N + Source)
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
                                .background(Amber400, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Compra #${order.purchaseNumber}",
                                color = Zinc950,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, Zinc700, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = AdminDashboardOrder.sourceLabel(order.source).uppercase(),
                                color = Zinc300,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Estado: ${AdminDashboardOrder.statusLabel(order.status)}",
                        fontSize = 14.sp,
                        color = Zinc100
                    )
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
                    val nextLabel = AdminDashboardOrder.nextStatusLabel(order.status)
                    if (nextLabel != null) {
                        Button(
                            onClick = onTransition,
                            enabled = !isTransitioning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber400,
                                contentColor = Zinc950
                            ),
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = if (isTransitioning) "Actualizando..." else nextLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
