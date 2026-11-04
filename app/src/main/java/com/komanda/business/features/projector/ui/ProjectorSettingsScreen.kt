package com.komanda.business.features.projector.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komanda.business.features.orders.OrderManager
import com.komanda.business.features.projector.NormalizedPoint
import com.komanda.business.features.projector.ProjectorCalibrationConfig
import com.komanda.business.features.projector.ProjectorConfigStore
import com.komanda.business.features.projector.ProjectorDisplayController
import com.komanda.business.features.projector.ProjectorShiftMode
import com.komanda.business.ui.theme.KomandaTokens
import com.komanda.business.ui.theme.Zinc300
import com.komanda.business.ui.theme.Zinc400
import com.komanda.business.ui.theme.Zinc800
import com.komanda.business.ui.theme.Zinc900
import com.komanda.business.ui.theme.Zinc950
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectorSettingsScreen(
    orderManager: OrderManager,
    configStore: ProjectorConfigStore,
    displayController: ProjectorDisplayController,
    tenantName: String? = null,
    onBack: () -> Unit
) {
    val orders by orderManager.orders.collectAsState()
    val connectionState by orderManager.connectionState.collectAsState()
    val config by configStore.config.collectAsState()
    val connectedDisplays by displayController.connectedDisplays.collectAsState()
    val isPresentingExternal by displayController.isPresentingExternal.collectAsState()

    var isFullscreenMirrorMode by remember { mutableStateOf(false) }
    var showHandlesInFullscreen by remember { mutableStateOf(true) }

    // MODO PANTALLA COMPLETA (Para Screen Mirroring directo del Telpo al DUB2800/DUB3800)
    if (isFullscreenMirrorMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            InteractiveWarpedPad(
                orders = orders,
                connectionState = connectionState,
                config = config,
                tenantName = tenantName,
                showHandles = showHandlesInFullscreen,
                onCornerMoved = { index, point ->
                    configStore.updateCorner(index, point)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Controles flotantes minimalistas
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        if (showHandlesInFullscreen) KomandaTokens.Surface.copy(alpha = 0.94f)
                        else Color.Black.copy(alpha = 0.45f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (showHandlesInFullscreen) KomandaTokens.Border else Color(0x22FFFFFF),
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedButton(
                    onClick = { isFullscreenMirrorMode = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = KomandaTokens.TextPrimary
                    ),
                    border = BorderStroke(1.dp, KomandaTokens.Border),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver al panel",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Panel POS", fontSize = 12.sp)
                }

                if (showHandlesInFullscreen) {
                    OutlinedButton(
                        onClick = { configStore.rotate180() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = KomandaTokens.SurfacePill,
                            contentColor = KomandaTokens.TextPrimary
                        ),
                        border = BorderStroke(1.dp, KomandaTokens.Border),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Rotar 180°", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { configStore.triggerBatchFlash() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = KomandaTokens.StatusReadyBg,
                            contentColor = Color(0xFF6EE7B7)
                        ),
                        border = BorderStroke(1.dp, KomandaTokens.StatusReady.copy(alpha = 0.4f)),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Flash Horno", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = { showHandlesInFullscreen = !showHandlesInFullscreen },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showHandlesInFullscreen) KomandaTokens.AccentSecondary else KomandaTokens.SurfacePill,
                        contentColor = if (showHandlesInFullscreen) KomandaTokens.AccentPrimary else KomandaTokens.TextSecondary
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (showHandlesInFullscreen) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showHandlesInFullscreen) "Ocultar Nodos (#000000)" else "Calibrar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Proyector de Piso & Mapping 3D",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KomandaTokens.TextPrimary
                        )
                        Text(
                            text = "Home Cinema DUB2800 / DUB3800 · Calibración de ángulo desde Telpo",
                            fontSize = 12.sp,
                            color = KomandaTokens.TextSecondary
                        )
                    }
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
                    OutlinedButton(
                        onClick = { displayController.openWirelessDisplaySettings() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = KomandaTokens.SurfacePill,
                            contentColor = KomandaTokens.TextPrimary
                        ),
                        border = BorderStroke(1.dp, KomandaTokens.Border),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vincular Proyector (Cast)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { isFullscreenMirrorMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KomandaTokens.AccentSecondary,
                            contentColor = KomandaTokens.AccentPrimary
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Proyectar en Pantalla", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Zinc950
                )
            )
        },
        containerColor = Zinc950
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. ESTADO DE PANTALLA INALÁMBRICA / MIRACAST (SECOND DISPLAY)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = BorderStroke(1.dp, KomandaTokens.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (isPresentingExternal) KomandaTokens.StatusReady
                                            else KomandaTokens.StatusPreparing,
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (isPresentingExternal && connectedDisplays.isNotEmpty()) {
                                        val d = connectedDisplays.first()
                                        "Proyector secundario activo: ${d.name} (${d.width}×${d.height})"
                                    } else {
                                        "Sin pantalla secundaria independiente detectada (Modo Espejo disponible)"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KomandaTokens.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Si conectás el Telpo por Miracast/Cast como segunda pantalla, el piso se proyecta automáticamente en segundo plano mientras seguís cobrando en el Telpo. Si tu receptor duplica la pantalla, usá el botón 'Proyectar en Pantalla'.",
                                fontSize = 13.sp,
                                color = Zinc400
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { displayController.refreshDisplays() },
                                modifier = Modifier
                                    .background(KomandaTokens.SurfacePill, CircleShape)
                                    .border(1.dp, KomandaTokens.Border, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Buscar pantallas",
                                    tint = Zinc300
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Zinc950, CircleShape)
                                    .border(1.dp, KomandaTokens.Border, CircleShape)
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Auto-Proyectar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = KomandaTokens.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = config.autoProjectOnExternalDisplay,
                                    onCheckedChange = {
                                        configStore.setAutoProject(it)
                                        displayController.refreshDisplays()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Zinc950,
                                        checkedTrackColor = KomandaTokens.AccentSecondary,
                                        uncheckedThumbColor = Zinc400,
                                        uncheckedTrackColor = Zinc800
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. PAD TÁCTIL DE CALIBRACIÓN DE 4 ESQUINAS (HOMOGRAFÍA EN VIVO)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = BorderStroke(1.dp, KomandaTokens.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Calibración de Perspectiva y Ángulo (Homografía 4 Puntos)",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KomandaTokens.TextPrimary
                                )
                                Text(
                                    text = "Arrastrá con el dedo los 4 nodos (TL, TR, BR, BL) mirando el piso hasta que el recuadro encaje derecho con tus baldosas.",
                                    fontSize = 13.sp,
                                    color = Zinc400
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { configStore.applyPreset("flat") },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = KomandaTokens.SurfacePill,
                                        contentColor = KomandaTokens.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, KomandaTokens.Border),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Plano 1:1", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { configStore.applyPreset("floor45") },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = KomandaTokens.SurfacePill,
                                        contentColor = KomandaTokens.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, KomandaTokens.Border),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Ángulo Piso 45°", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { configStore.applyPreset("corner") },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = KomandaTokens.SurfacePill,
                                        contentColor = KomandaTokens.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, KomandaTokens.Border),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Esquina Diagonal", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { configStore.rotate180() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KomandaTokens.AccentSecondary,
                                        contentColor = KomandaTokens.AccentPrimary
                                    ),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Rotar 180° (Invertir)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // LIENZO INTERACTIVO 16:10 CON LOS 4 NODOS TÁCTILES
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .border(1.5.dp, KomandaTokens.Border, RoundedCornerShape(20.dp))
                        ) {
                            InteractiveWarpedPad(
                                orders = orders,
                                connectionState = connectionState,
                                config = config,
                                tenantName = tenantName,
                                showHandles = true,
                                onCornerMoved = { idx, pt ->
                                    configStore.updateCorner(idx, pt)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // SLIDER DE COMPENSACIÓN ANAMÓRFICA (STRETCH Y)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Compensación de ángulo visual del cliente (Stretch Y):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = KomandaTokens.TextSecondary
                            )
                            Slider(
                                value = config.verticalStretch,
                                onValueChange = { configStore.updateVerticalStretch(it) },
                                valueRange = 0.90f..1.60f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = KomandaTokens.AccentSecondary,
                                    activeTrackColor = KomandaTokens.AccentSecondary,
                                    inactiveTrackColor = Zinc800
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fx", config.verticalStretch),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = KomandaTokens.TextPrimary
                            )
                        }
                    }
                }
            }

            // 3. LOOP POR FRANJA HORARIA NOCTURNA + GATILLO DE TANDA DE HORNO
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Zinc900),
                    border = BorderStroke(1.dp, KomandaTokens.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Modo por Franja Horaria (01:00 a 08:00) & Aviso de Horno",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KomandaTokens.TextPrimary
                                )
                                Text(
                                    text = "Cambia el mensaje destacado en el piso según la hora de la madrugada o dispara un flash de 10 segundos cuando sale una tanda.",
                                    fontSize = 13.sp,
                                    color = Zinc400
                                )
                            }

                            Button(
                                onClick = { configStore.triggerBatchFlash() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KomandaTokens.StatusReady,
                                    contentColor = Zinc950
                                ),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "⚡ Disparar Flash: Recién Salido del Horno (10s)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProjectorShiftMode.entries.forEach { mode ->
                                val selected = config.shiftMode == mode
                                if (selected) {
                                    Button(
                                        onClick = { configStore.setShiftMode(mode) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KomandaTokens.AccentSecondary,
                                            contentColor = KomandaTokens.AccentPrimary
                                        ),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(mode.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { configStore.setShiftMode(mode) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = KomandaTokens.SurfacePill,
                                            contentColor = Zinc300
                                        ),
                                        border = BorderStroke(1.dp, KomandaTokens.Border),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(mode.label, fontSize = 13.sp)
                                    }
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
private fun InteractiveWarpedPad(
    orders: List<com.komanda.business.core.model.AdminDashboardOrder>,
    connectionState: com.komanda.business.core.network.ConnectionState,
    config: ProjectorCalibrationConfig,
    tenantName: String?,
    showHandles: Boolean,
    onCornerMoved: (Int, NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        ProjectorFloorCanvas(
            orders = orders,
            connectionState = connectionState,
            config = config,
            tenantName = tenantName,
            showCalibrationGuides = showHandles,
            modifier = Modifier.fillMaxSize()
        )

        if (showHandles) {
            val labels = listOf("TL", "TR", "BR", "BL")
            val handleSize = 48.dp
            val handleRadiusPx = with(density) { (handleSize / 2).toPx() }

            config.corners.forEachIndexed { index, corner ->
                val currentCorner by rememberUpdatedState(corner)
                val currentOnMoved by rememberUpdatedState(onCornerMoved)

                val centerX = corner.x * widthPx
                val centerY = corner.y * heightPx

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (centerX - handleRadiusPx).roundToInt(),
                                (centerY - handleRadiusPx).roundToInt()
                            )
                        }
                        .size(handleSize)
                        .background(KomandaTokens.AccentSecondary, CircleShape)
                        .border(3.dp, KomandaTokens.AccentPrimary, CircleShape)
                        .pointerInput(index, widthPx, heightPx) {
                            var dragX = currentCorner.x * widthPx
                            var dragY = currentCorner.y * heightPx
                            detectDragGestures(
                                onDragStart = {
                                    dragX = currentCorner.x * widthPx
                                    dragY = currentCorner.y * heightPx
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragX = (dragX + dragAmount.x).coerceIn(0f, widthPx)
                                    dragY = (dragY + dragAmount.y).coerceIn(0f, heightPx)
                                    currentOnMoved(
                                        index,
                                        NormalizedPoint(
                                            x = dragX / widthPx,
                                            y = dragY / heightPx
                                        )
                                    )
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[index],
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = KomandaTokens.AccentPrimary
                    )
                }
            }
        }
    }
}
