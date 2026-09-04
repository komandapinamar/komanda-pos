package com.komanda.business.core.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komanda.business.core.network.MobileTenantDto
import com.komanda.business.ui.theme.Amber400
import com.komanda.business.ui.theme.Zinc300
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc700
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950

@Composable
fun TenantSelectionScreen(
    tenants: List<MobileTenantDto>,
    onSelectTenant: (tenantId: String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Zinc950),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar Restaurante",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Zinc300
                )

                Text(
                    text = "Elegí el restaurante para operar esta terminal:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc400,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tenants, key = { it.id }) { tenant ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTenant(tenant.id) },
                            colors = CardDefaults.cardColors(containerColor = Zinc800),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tenant.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Zinc300
                                    )
                                    val locationLabel = tenant.primaryLocation?.name
                                        ?: "Sin sucursal asignada"
                                    Text(
                                        text = "Sucursal: $locationLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Zinc400
                                    )
                                }

                                Text(
                                    text = tenant.role.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Amber400,
                                    modifier = Modifier
                                        .background(Zinc700, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.textButtonColors(contentColor = Zinc400)
                ) {
                    Text("Cerrar sesión", fontSize = 14.sp)
                }
            }
        }
    }
}
