package com.komanda.business.features.settings.ui

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komanda.business.hardware.printing.model.PrintResult
import com.komanda.business.hardware.printing.enums.PrintTrigger
import com.komanda.business.hardware.printing.model.PrinterConfig
import com.komanda.business.hardware.printing.enums.PrinterRole
import com.komanda.business.hardware.printing.PrinterRouter
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.ui.theme.Amber400
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    router: PrinterRouter,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printers by router.printers.collectAsStateWithLifecycle()
    var showAddNetworkDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración de Impresoras",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Zinc300
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddNetworkDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber400,
                            contentColor = Zinc950
                        ),
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Agregar Impresora de Red", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Zinc950)
            )
        },
        containerColor = Zinc950
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Dispositivos Configurados (${printers.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Zinc400
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Asigná el tipo de comanda (Cocina o Mostrador) y las reglas de disparo automático por cada impresora.",
                    fontSize = 13.sp,
                    color = Zinc600
                )
            }

            items(printers, key = { it.id }) { config ->
                PrinterConfigCard(
                    config = config,
                    onUpdate = { updated -> router.repository.savePrinter(updated) },
                    onDelete = { router.repository.removePrinter(config.id) },
                    onTestPrint = {
                        scope.launch {
                            val res = router.printTestTicket(config)
                            val msg = when (res) {
                                is PrintResult.Success -> "Impresión de prueba enviada con éxito a ${config.name}"
                                is PrintResult.Error -> "Error: ${res.message}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showAddNetworkDialog) {
        AddNetworkPrinterDialog(
            onDismiss = { showAddNetworkDialog = false },
            onAdd = { name, host, port ->
                val id = "net_${host}_$port"
                val newConfig = PrinterConfig(
                    id = id,
                    name = name.ifBlank { "Impresora Red ($host)" },
                    type = PrinterType.NETWORK_ESC_POS,
                    role = PrinterRole.KITCHEN,
                    trigger = PrintTrigger.ON_NEW_ORDER,
                    address = "$host:$port",
                    copies = 1
                )
                router.repository.savePrinter(newConfig)
                showAddNetworkDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterConfigCard(
    config: PrinterConfig,
    onUpdate: (PrinterConfig) -> Unit,
    onDelete: () -> Unit,
    onTestPrint: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Zinc800, RoundedCornerShape(2.dp)),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        shape = RoundedCornerShape(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name, Type Badge, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    config.address?.let { addr ->
                        Text(
                            text = addr,
                            fontSize = 12.sp,
                            color = Zinc400
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeBadge(config.type)

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Red400)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selectors: Role and Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Role Selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tipo de Comanda (Rol):", fontSize = 12.sp, color = Zinc400, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    RoleDropdown(
                        selected = config.role,
                        onSelect = { onUpdate(config.copy(role = it)) }
                    )
                }

                // Trigger Selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Impresión Automática:", fontSize = 12.sp, color = Zinc400, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    TriggerDropdown(
                        selected = config.trigger,
                        onSelect = { onUpdate(config.copy(trigger = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Test Print
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onTestPrint,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Zinc300),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = Zinc300)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar Impresión", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: PrinterType) {
    val (label, bg, fg) = when (type) {
        PrinterType.USB_ESC_POS -> Triple("USB", Zinc800, Zinc100)
        PrinterType.NETWORK_ESC_POS -> Triple("RED TCP", Zinc800, Amber400)
        PrinterType.BLUETOOTH_ESC_POS -> Triple("BLUETOOTH", Zinc800, Emerald600)
        PrinterType.TELPO_INTERNAL -> Triple("TELPO", Zinc800, Zinc100)
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(2.dp))
            .border(1.dp, Zinc700, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(
    selected: PrinterRole,
    onSelect: (PrinterRole) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = when (selected) {
                PrinterRole.KITCHEN -> "Cocina (Sin Precios)"
                PrinterRole.COUNTER -> "Mostrador (Comercial)"
                PrinterRole.DISABLED -> "Desactivada"
            },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Zinc950,
                unfocusedContainerColor = Zinc950,
                focusedBorderColor = Amber400,
                unfocusedBorderColor = Zinc800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Zinc900)
        ) {
            DropdownMenuItem(
                text = { Text("Cocina (Sin Precios)", color = Color.White, fontSize = 13.sp) },
                onClick = { onSelect(PrinterRole.KITCHEN); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Mostrador (Comercial)", color = Color.White, fontSize = 13.sp) },
                onClick = { onSelect(PrinterRole.COUNTER); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Desactivada", color = Red400, fontSize = 13.sp) },
                onClick = { onSelect(PrinterRole.DISABLED); expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerDropdown(
    selected: PrintTrigger,
    onSelect: (PrintTrigger) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = when (selected) {
                PrintTrigger.ON_NEW_ORDER -> "Al ingresar pedido"
                PrintTrigger.ON_DIRECT_POS_ORDER -> "Al crear en mostrador"
                PrintTrigger.ALWAYS_AUTOMATIC -> "Siempre automático"
                PrintTrigger.MANUAL_ONLY -> "Solo manual"
            },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Zinc950,
                unfocusedContainerColor = Zinc950,
                focusedBorderColor = Amber400,
                unfocusedBorderColor = Zinc800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Zinc900)
        ) {
            DropdownMenuItem(
                text = { Text("Al ingresar pedido", color = Color.White, fontSize = 13.sp) },
                onClick = { onSelect(PrintTrigger.ON_NEW_ORDER); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Al crear en mostrador", color = Color.White, fontSize = 13.sp) },
                onClick = { onSelect(PrintTrigger.ON_DIRECT_POS_ORDER); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Siempre automático", color = Color.White, fontSize = 13.sp) },
                onClick = { onSelect(PrintTrigger.ALWAYS_AUTOMATIC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Solo manual", color = Zinc400, fontSize = 13.sp) },
                onClick = { onSelect(PrintTrigger.MANUAL_ONLY); expanded = false }
            )
        }
    }
}

@Composable
private fun AddNetworkPrinterDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, host: String, port: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Impresora de Red", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre descriptivo (ej: Cocina Parrilla)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Dirección IP (ej: 192.168.1.150)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Puerto (por defecto 9100)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        val portInt = port.toIntOrNull() ?: 9100
                        onAdd(name, host.trim(), portInt)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Zinc950)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Zinc400)
            }
        },
        containerColor = Zinc900
    )
}

private val Zinc600 = Color(0xFF52525B)
