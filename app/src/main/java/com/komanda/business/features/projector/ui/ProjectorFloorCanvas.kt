package com.komanda.business.features.projector.ui

import android.graphics.Matrix as AndroidMatrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.komanda.business.R
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.OrderStatuses
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.features.projector.ProjectorCalibrationConfig
import com.komanda.business.ui.theme.KomandaTokens
import kotlinx.coroutines.delay

/**
 * Pure Jetpack Compose renderer of the Komanda Floor Projection Surface.
 *
 * Projects the looping Refill animation (`R.raw.projector_refill_loop` / `Frame 13 (1).mp4`)
 * over a pitch-black `#000000` background so the projector emits zero light outside the logo
 * and discreetly displays ready order numbers underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectorFloorCanvas(
    orders: List<AdminDashboardOrder>,
    @Suppress("UNUSED_PARAMETER") connectionState: ConnectionState,
    config: ProjectorCalibrationConfig,
    @Suppress("UNUSED_PARAMETER") tenantName: String? = null,
    showCalibrationGuides: Boolean = false,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    var seenReadyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentlyReadyUntil by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    val readyOrders = remember(orders) {
        orders.filter {
            !it.isCashPending &&
                it.paymentStatus == "paid" &&
                it.status == OrderStatuses.READY
        }.take(8)
    }

    LaunchedEffect(readyOrders) {
        val currentIds = readyOrders.map { it.id }.toSet()
        if (seenReadyIds.isNotEmpty()) {
            val newlyAdded = currentIds - seenReadyIds
            if (newlyAdded.isNotEmpty()) {
                val expiry = System.currentTimeMillis() + 8_000L
                recentlyReadyUntil = recentlyReadyUntil + newlyAdded.associateWith { expiry }
            }
        }
        seenReadyIds = currentIds
    }

    val isBatchFlashActive = nowMillis < config.batchFlashUntilMillis

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val scale = (maxWidth.value / 1100f).coerceIn(0.32f, 1.6f)
        val contentMaxWidth = maxWidth
        val contentMaxHeight = maxHeight
        fun sDp(value: Float): Dp = (value * scale).dp
        fun sSp(value: Float): TextUnit = (value * scale).sp

        if (showCalibrationGuides && config.showFloorGridInCalibration) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = size.width / 14f
                val gridColor = Color(0x12FFFFFF)
                var x = 0f
                while (x <= size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
        }

        // Homography-warped pitch-black surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    if (w > 1f && h > 1f) {
                        val src = floatArrayOf(
                            0f, 0f,
                            w, 0f,
                            w, h,
                            0f, h
                        )
                        val dst = floatArrayOf(
                            config.topLeft.x * w, config.topLeft.y * h,
                            config.topRight.x * w, config.topRight.y * h,
                            config.bottomRight.x * w, config.bottomRight.y * h,
                            config.bottomLeft.x * w, config.bottomLeft.y * h
                        )
                        val homography = AndroidMatrix()
                        if (homography.setPolyToPoly(src, 0, dst, 0, 4)) {
                            val nativeCanvas = drawContext.canvas.nativeCanvas
                            val saveCount = nativeCanvas.save()
                            nativeCanvas.concat(homography)
                            drawContent()
                            nativeCanvas.restoreToCount(saveCount)
                        } else {
                            drawContent()
                        }
                    } else {
                        drawContent()
                    }
                }
                .background(Color.Black)
                .padding(horizontal = sDp(6f), vertical = sDp(4f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Looping Refill Video (Frame 13 (1).mp4), fitted as large as possible
                //    with full uncropped margins around all four edges of the "R".
                val videoRatio = 1240f / 920f
                val stretch = config.verticalStretch.coerceIn(0.9f, 1.6f)
                val textReserve = (if (readyOrders.isEmpty()) sDp(30f) else sDp(74f)) * stretch
                val availableHeight = contentMaxHeight - sDp(10f) - textReserve
                val videoWidth = minOf(contentMaxWidth - sDp(12f), availableHeight * videoRatio)
                val videoHeight = videoWidth / videoRatio
                RefillLoopVideo(
                    modifier = Modifier.size(videoWidth, videoHeight)
                )

                Spacer(modifier = Modifier.height(sDp(10f)))

                // 2. Discreet "Seguí tu pedido" + Ready Order Numbers
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(sDp(10f))
                ) {
                    Text(
                        text = "Seguí tu pedido",
                        fontSize = sSp(16f),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF71717A),
                        modifier = Modifier.graphicsLayer {
                            scaleY = config.verticalStretch
                        }
                    )

                    if (readyOrders.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(sDp(12f), Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(sDp(10f))
                        ) {
                            readyOrders.forEach { order ->
                                val isJustReady = (recentlyReadyUntil[order.id] ?: 0L) > nowMillis
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF09090B),
                                            RoundedCornerShape(sDp(14f))
                                        )
                                        .border(
                                            width = if (isJustReady) sDp(2f) else sDp(1f),
                                            color = if (isJustReady) KomandaTokens.StatusReady else Color(0xFF27272A),
                                            shape = RoundedCornerShape(sDp(14f))
                                        )
                                        .padding(horizontal = sDp(14f), vertical = sDp(6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${order.purchaseNumber}",
                                        fontSize = sSp(28f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isJustReady) Color(0xFF6EE7B7) else Color(0xFFF4F3F1),
                                        modifier = Modifier.graphicsLayer {
                                            scaleY = config.verticalStretch
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional 10-second oven flash overlay
            if (isBatchFlashActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(sDp(24f)))
                        .background(Color.Black)
                        .border(sDp(2f), KomandaTokens.PrismRingBrush, RoundedCornerShape(sDp(24f)))
                        .padding(sDp(32f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(sDp(16f))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(KomandaTokens.StatusReadyBg, CircleShape)
                                .border(sDp(1f), KomandaTokens.StatusReady, CircleShape)
                                .padding(horizontal = sDp(18f), vertical = sDp(6f))
                        ) {
                            Text(
                                text = "● RECIÉN SALIDO DEL HORNO",
                                fontSize = sSp(15f),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6EE7B7)
                            )
                        }

                        Text(
                            text = config.batchFlashHeadline,
                            fontSize = sSp(52f),
                            lineHeight = sSp(56f),
                            fontWeight = FontWeight.Bold,
                            color = KomandaTokens.TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleY = config.verticalStretch
                            }
                        )
                    }
                }
            }
        }

        // Calibration polygon outline when in calibration mode
        if (showCalibrationGuides) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tl = Offset(config.topLeft.x * size.width, config.topLeft.y * size.height)
                val tr = Offset(config.topRight.x * size.width, config.topRight.y * size.height)
                val br = Offset(config.bottomRight.x * size.width, config.bottomRight.y * size.height)
                val bl = Offset(config.bottomLeft.x * size.width, config.bottomLeft.y * size.height)

                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(
                    path = path,
                    color = Color(0x99F4F3F1),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )
            }
        }
    }
}

@Composable
private fun RefillLoopVideo(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerHolder = remember {
        object {
            var mediaPlayer: MediaPlayer? = null
            var surface: Surface? = null

            fun attachAndStart(surfaceTexture: SurfaceTexture) {
                release()
                val newSurface = Surface(surfaceTexture)
                surface = newSurface
                val mp = MediaPlayer()
                mediaPlayer = mp
                runCatching {
                    val afd = context.resources.openRawResourceFd(R.raw.projector_refill_loop)
                        ?: return
                    afd.use { descriptor ->
                        mp.setDataSource(
                            descriptor.fileDescriptor,
                            descriptor.startOffset,
                            descriptor.length
                        )
                    }
                    mp.setSurface(newSurface)
                    mp.setVolume(0f, 0f)
                    mp.isLooping = true
                    mp.setOnPreparedListener { player ->
                        runCatching { player.start() }
                    }
                    mp.setOnCompletionListener { player ->
                        runCatching {
                            player.seekTo(0)
                            player.start()
                        }
                    }
                    mp.setOnErrorListener { _, _, _ -> true }
                    mp.prepareAsync()
                }
            }

            fun release() {
                runCatching {
                    mediaPlayer?.stop()
                }
                runCatching {
                    mediaPlayer?.release()
                }
                mediaPlayer = null
                runCatching {
                    surface?.release()
                }
                surface = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerHolder.release()
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        playerHolder.attachAndStart(st)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        playerHolder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
                if (isAvailable) {
                    surfaceTexture?.let { playerHolder.attachAndStart(it) }
                }
            }
        }
    )
}
