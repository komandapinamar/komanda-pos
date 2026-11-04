package com.komanda.business.features.pos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.komanda.business.core.network.CatalogItemDto
import com.komanda.business.features.pos.DirectOrderResult
import com.komanda.business.features.pos.PosManager
import com.komanda.business.ui.theme.KomandaTokens
import com.komanda.business.ui.theme.Red400
import com.komanda.business.ui.theme.Red700
import com.komanda.business.ui.theme.Red900
import com.komanda.business.ui.theme.Zinc300
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc600
import com.komanda.business.ui.theme.Zinc700
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950
import kotlinx.coroutines.launch

@Composable
fun PosScreen(
    posManager: PosManager,
    onOrderCreated: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories by posManager.categories.collectAsState()
    val items by posManager.items.collectAsState()
    val isLoading by posManager.isLoading.collectAsState()
    val quantities by posManager.quantities.collectAsState()
    val customerName by posManager.customerName.collectAsState()
    val discountCode by posManager.discountCode.collectAsState()
    val notes by posManager.notes.collectAsState()
    val isSubmitting by posManager.submitting.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        posManager.loadCatalog()
    }

    val totalUnits = remember(quantities) { quantities.values.sum() }
    val cartSubtotal = remember(items, quantities) {
        quantities.entries.sumOf { (id, qty) ->
            val item = items.find { it.id == id }
            val price = item?.price?.toDoubleOrNull() ?: 0.0
            price * qty
        }
    }

    Scaffold(
        containerColor = Zinc950,
        bottomBar = {
            Surface(
                color = Zinc900,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, KomandaTokens.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (totalUnits > 0) {
                                "$totalUnits producto${if (totalUnits != 1) "s" else ""}"
                            } else {
                                "Ningún producto seleccionado"
                            },
                            color = KomandaTokens.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (totalUnits > 0 && cartSubtotal > 0) {
                            Text(
                                text = "Total: $${cartSubtotal.toLong()}",
                                color = KomandaTokens.AccentTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            scope.launch {
                                val result = posManager.submitDirectOrder()
                                when (result) {
                                    is DirectOrderResult.Success -> {
                                        onOrderCreated()
                                    }
                                    is DirectOrderResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting && totalUnits > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KomandaTokens.AccentTertiary,
                            contentColor = KomandaTokens.AccentPrimary,
                            disabledContainerColor = KomandaTokens.AccentTertiary.copy(alpha = 0.35f),
                            disabledContentColor = KomandaTokens.AccentPrimary.copy(alpha = 0.5f)
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (isSubmitting) "Creando pedido..." else "Crear pedido directo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header: Back button + Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(KomandaTokens.SurfacePill, CircleShape)
                            .border(1.dp, KomandaTokens.Border, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a pedidos",
                            tint = Zinc300
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "MOSTRADOR · VENTA DIRECTA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.12.em,
                            color = Zinc400
                        )
                        Text(
                            text = "Crear pedido directo",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.02).em,
                            color = KomandaTokens.TextPrimary
                        )
                    }
                }
            }

            // Error Message (if any)
            if (errorMessage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Red900.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .border(1.dp, Red400.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Red400,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Section "Productos"
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = BorderStroke(1.25.dp, KomandaTokens.PrismRingBrush)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Productos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = KomandaTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = KomandaTokens.AccentTertiary,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Cargando productos...",
                                        color = Zinc400,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else if (items.isEmpty()) {
                            Text(
                                text = "No hay productos activos en el catálogo.",
                                color = Zinc400,
                                fontSize = 14.sp
                            )
                        } else {
                            // Group by categories exactly like AdminDirectOrderForm.tsx
                            categories.forEach { category ->
                                val categoryItems = items.filter { it.categoryId == category.id }
                                if (categoryItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = category.name.uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.05.em,
                                        color = KomandaTokens.AccentTertiary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        categoryItems.forEach { item ->
                                            DirectOrderCatalogItemRow(
                                                item = item,
                                                quantity = quantities[item.id] ?: 0,
                                                onQuantityChange = { nextQty ->
                                                    posManager.handleQuantityChange(item.id, nextQty)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Items without category
                            val uncategorized = items.filter { it.categoryId == null }
                            if (uncategorized.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "VARIOS",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.05.em,
                                    color = KomandaTokens.AccentTertiary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uncategorized.forEach { item ->
                                        DirectOrderCatalogItemRow(
                                            item = item,
                                            quantity = quantities[item.id] ?: 0,
                                            onQuantityChange = { nextQty ->
                                                posManager.handleQuantityChange(item.id, nextQty)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section "Datos del cliente"
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = BorderStroke(1.dp, KomandaTokens.Border)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Datos del cliente",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = KomandaTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Nombre del cliente (opcional, por defecto: NN)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Zinc400
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { posManager.setCustomerName(it) },
                            placeholder = { Text("Ej: Juan Pérez o NN", color = Zinc600) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KomandaTokens.AccentTertiary.copy(alpha = 0.45f),
                                unfocusedBorderColor = Zinc700,
                                focusedContainerColor = KomandaTokens.SurfaceDeep,
                                unfocusedContainerColor = KomandaTokens.SurfaceDeep,
                                focusedTextColor = KomandaTokens.TextPrimary,
                                unfocusedTextColor = KomandaTokens.TextPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Código de descuento (opcional)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Zinc400
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = discountCode,
                            onValueChange = { posManager.setDiscountCode(it.uppercase()) },
                            placeholder = { Text("Ej: PROMO10", color = Zinc600) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KomandaTokens.AccentTertiary.copy(alpha = 0.45f),
                                unfocusedBorderColor = Zinc700,
                                focusedContainerColor = KomandaTokens.SurfaceDeep,
                                unfocusedContainerColor = KomandaTokens.SurfaceDeep,
                                focusedTextColor = KomandaTokens.TextPrimary,
                                unfocusedTextColor = KomandaTokens.TextPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Notas del pedido",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Zinc400
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { posManager.setNotes(it) },
                            placeholder = { Text("Ej: sin sal, bien cocido...", color = Zinc600) },
                            minLines = 3,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KomandaTokens.AccentTertiary.copy(alpha = 0.45f),
                                unfocusedBorderColor = Zinc700,
                                focusedContainerColor = KomandaTokens.SurfaceDeep,
                                unfocusedContainerColor = KomandaTokens.SurfaceDeep,
                                focusedTextColor = KomandaTokens.TextPrimary,
                                unfocusedTextColor = KomandaTokens.TextPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectOrderCatalogItemRow(
    item: CatalogItemDto,
    quantity: Int,
    onQuantityChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onQuantityChange(quantity + 1) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KomandaTokens.SurfaceDeep),
        border = BorderStroke(1.dp, if (quantity > 0) KomandaTokens.AccentTertiary.copy(alpha = 0.35f) else Zinc700)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = KomandaTokens.TextPrimary
                )
                Text(
                    text = "$${item.price}",
                    fontSize = 12.sp,
                    color = Zinc400
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onQuantityChange(quantity - 1) },
                    enabled = quantity > 0,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, KomandaTokens.Border),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("-", fontSize = 16.sp, color = if (quantity > 0) Color.White else Zinc600)
                }

                Text(
                    text = "$quantity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedButton(
                    onClick = { onQuantityChange(quantity + 1) },
                    enabled = quantity < 50,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, KomandaTokens.Border),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+", fontSize = 16.sp, color = if (quantity < 50) Color.White else Zinc600)
                }
            }
        }
    }
}
