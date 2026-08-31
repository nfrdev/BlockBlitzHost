package com.nfrdev.blockblitzhost.blockblitz

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfrdev.blockblitzhost.blockBlitzDataStore
import kotlinx.coroutines.delay

private val BrickColors = listOf(
    Color(0xFF06B6D4), // 0: Cyan (I Piece)
    Color(0xFF3B82F6), // 1: Blue (J Piece)
    Color(0xFFF97316), // 2: Orange (L Piece)
    Color(0xFFF59E0B), // 3: Yellow (O Piece)
    Color(0xFF10B981), // 4: Green (S Piece)
    Color(0xFF8B5CF6), // 5: Purple (T Piece)
    Color(0xFFEF4444)  // 6: Red (Z Piece)
)

private data class ParticleData(
    val xRatio: Float,
    val yRatio: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float
)

private class BlockBlitzViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return BlockBlitzViewModel(
            application = context.applicationContext as android.app.Application,
            dataStore = context.applicationContext.blockBlitzDataStore,
            savedStateHandle = extras.createSavedStateHandle(),
        ) as T
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BlockBlitzScreen(
    viewModel: BlockBlitzViewModel,
    onExit: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    var gameUIReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        gameUIReady = true
    }
    val currentMode by viewModel.gameMode.collectAsStateWithLifecycle()

    LaunchedEffect(state.gameStatus) {
        if (state.gameStatus == GameStatus.GameOver) {
            onRequestNotificationPermission()
        }
    }

    val blitzTime by viewModel.blitzTimeRemaining.collectAsStateWithLifecycle()
    val currentTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
    val activeBrickColors = remember(currentTheme) { getThemeColors(currentTheme) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val uiState = state

    BackHandler {
        when {
            uiState.isRunning -> viewModel.dispatch(Action.Pause)
            else -> onExit()
        }
    }

    var scorePopVisible by remember { mutableStateOf(false) }
    var scorePopText by remember { mutableStateOf("") }
    val scorePopAlpha by animateFloatAsState(
        targetValue = if (scorePopVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "ScorePopAlpha"
    )
    val scorePopOffsetY by animateFloatAsState(
        targetValue = if (scorePopVisible) -30f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "ScorePopOffsetY"
    )

    LaunchedEffect(state.scorePopCounter) {
        if (state.scorePopCounter > 0 && state.scorePopAmount > 0) {
            scorePopText = "+${state.scorePopAmount}"
            scorePopVisible = true
            delay(600)
            scorePopVisible = false
        }
    }

    val flashAlpha by animateFloatAsState(
        targetValue = if (state.gameStatus == GameStatus.LineClearing) 0.7f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "LineClearFlash"
    )


    var particles by remember { mutableStateOf(listOf<ParticleData>()) }
    LaunchedEffect(state.gameStatus) {
        if (state.gameStatus == GameStatus.GameOver) {
            try {
                if (state.isHaptic) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            } catch (_: Exception) {}
        }

        if (state.gameStatus == GameStatus.LineClearing) {
            try {
                if (state.isHaptic) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            } catch (_: Exception) {}

            val newParticles = List(28) {
                ParticleData(
                    xRatio = (0.1f + (it % 7) * 0.12f),
                    yRatio = (0.3f + (it % 4) * 0.15f),
                    vx = (-4f + (it % 9)),
                    vy = (-6f + (it % 11)),
                    color = activeBrickColors[it % activeBrickColors.size],
                    size = 4f + (it % 5) * 2f
                )
            }
            particles = newParticles
            delay(300)
            particles = emptyList()
        }
        
        // Combo milestone haptic feedback
        if (state.combo > 0 && (state.combo == 5 || state.combo == 10 || state.combo % 10 == 0)) {
            try {
                if (state.isHaptic) {
                    HapticPatterns.comboPattern(haptic, state.combo)
                }
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "InGameEffects")
    val blitzWarningGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlitzWarningGlow"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = Color(0xFF0F172A),
    ) {
        AnimatedVisibility(
            visible = gameUIReady,
            enter = fadeIn(tween(800)) + scaleIn(initialScale = 1.05f, animationSpec = tween(800, easing = EaseOutQuart))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Mode-specific Background Glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.12f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(currentMode.accentColor, Color.Transparent),
                                radius = 2500f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BLOCK BLITZ",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFEC4899))
                            )
                        ),
                        modifier = if (sharedTransitionScope != null && animatedContentScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "title"),
                                    animatedVisibilityScope = animatedContentScope
                                )
                            }
                        } else Modifier
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentMode.accentColor.copy(alpha = 0.15f))
                            .border(1.dp, currentMode.accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = currentMode.icon,
                                contentDescription = null,
                                tint = currentMode.accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = currentMode.title.uppercase(),
                                color = currentMode.accentColor,
                                fontSize = 10.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Pause Icon Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x26FFFFFF), CircleShape)
                        .clickable {
                            if (state.isRunning) viewModel.dispatch(Action.Pause)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val matrixWidth = state.matrix.first.toFloat()
            val matrixHeight = state.matrix.second.toFloat()
            val previewSize = 4 to 4

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.size(68.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "HOLD",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .padding(top = 2.dp)
                                .alpha(if (state.hasHeld) 0.4f else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.heldSpirit == Spirit.Empty) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(
                                            width = 1.dp,
                                            color = Color(0x33FFFFFF),
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "HOLD",
                                        color = Color(0x26FFFFFF),
                                        fontSize = 7.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                            } else {
                                NextPiecePreview(
                                    spirit = state.heldSpirit,
                                    matrix = previewSize,
                                    brickColors = activeBrickColors
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CompactStatItem(label = "SCORE", value = "${state.score}", color = currentMode.accentColor)
                            if (scorePopAlpha > 0f) {
                                Text(
                                    text = scorePopText,
                                    color = currentMode.accentColor,
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    modifier = Modifier
                                        .offset(y = scorePopOffsetY.dp)
                                        .alpha(scorePopAlpha)
                                )
                            }
                        }
                        CompactStatDivider()
                        CompactStatItem(label = "LINES", value = "${state.line}", color = Color(0xFFEC4899))
                        CompactStatDivider()
                        if (currentMode == GameMode.Blitz) {
                            val mins = blitzTime / 60
                            val secs = blitzTime % 60
                            val timeFormatted = "${if (mins < 10) "0$mins" else "$mins"}:${if (secs < 10) "0$secs" else "$secs"}"
                            CompactStatItem(
                                label = "TIME",
                                value = timeFormatted,
                                color = if (blitzTime <= 15) Color(0xFFEF4444) else currentMode.accentColor
                            )
                        } else if (currentMode == GameMode.Zen) {
                            CompactStatItem(label = "MODE", value = "ZEN", color = currentMode.accentColor)
                        } else if (currentMode == GameMode.DailyChallenge) {
                            CompactStatItem(label = "MODE", value = "DAILY", color = currentMode.accentColor)
                        } else {
                            CompactStatItem(label = "LEVEL", value = "${state.level}", color = currentMode.accentColor)
                        }
                        CompactStatDivider()
                        CompactStatItem(label = "BEST", value = "${state.highScore}", color = Color(0xFFF59E0B))
                    }
                }

                Card(
                    modifier = Modifier
                        .height(68.dp)
                        .width(116.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NEXT",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val reserve = state.spiritReserve.take(3)
                            for (i in 0 until 3) {
                                val nextSpirit = reserve.getOrNull(i)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .alpha(if (i == 0) 1f else if (i == 1) 0.7f else 0.45f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (nextSpirit != null) {
                                        NextPiecePreview(
                                            spirit = nextSpirit,
                                            matrix = previewSize,
                                            brickColors = activeBrickColors
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val isBlitzWarning = currentMode == GameMode.Blitz && blitzTime <= 10 && state.isRunning
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(matrixWidth / matrixHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF070A12))
                        .border(
                            width = if (isBlitzWarning) 2.5.dp else 1.5.dp,
                            color = if (isBlitzWarning) Color(0xFFEF4444).copy(alpha = blitzWarningGlow) 
                                    else currentMode.accentColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(state.gameStatus) {
                                if (!state.isRunning) return@pointerInput
                                var touchStartX = 0f
                                var touchStartY = 0f
                                var totalDragX = 0f
                                var totalDragY = 0f
                                val moveThreshold = 28.dp.toPx()
                                val dropThreshold = 36.dp.toPx()

                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown()
                                        touchStartX = down.position.x
                                        touchStartY = down.position.y
                                        totalDragX = 0f
                                        totalDragY = 0f

                                        var dragPointer = down
                                        var isTap = true

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == dragPointer.id }
                                            if (change == null || !change.pressed) break

                                            val deltaX = change.position.x - touchStartX
                                            val deltaY = change.position.y - touchStartY

                                            if (abs(deltaX) > 10.dp.toPx() || abs(deltaY) > 10.dp.toPx()) {
                                                isTap = false
                                            }

                                            totalDragX += change.position.x - touchStartX
                                            totalDragY += change.position.y - touchStartY
                                            touchStartX = change.position.x
                                            touchStartY = change.position.y

                                            while (totalDragX > moveThreshold) {
                                                viewModel.dispatch(Action.Move(Direction.Right))
                                                try {
                                                    if (state.isHaptic) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    }
                                                } catch (_: Exception) {}
                                                totalDragX -= moveThreshold
                                            }
                                            while (totalDragX < -moveThreshold) {
                                                viewModel.dispatch(Action.Move(Direction.Left))
                                                try {
                                                    if (state.isHaptic) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    }
                                                } catch (_: Exception) {}
                                                totalDragX += moveThreshold
                                            }
                                            while (totalDragY > dropThreshold) {
                                                viewModel.dispatch(Action.Move(Direction.Down))
                                                try {
                                                    if (state.isHaptic) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    }
                                                } catch (_: Exception) {}
                                                totalDragY -= dropThreshold
                                            }
                                        }

                                        if (isTap) {
                                            viewModel.dispatch(Action.Rotate)
                                            try {
                                                if (state.isHaptic) {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                    ) {
                        val cellWidth = size.width / state.matrix.first
                        val cellHeight = size.height / state.matrix.second

                        for (x in 0 until state.matrix.first) {
                            for (y in 0 until state.matrix.second) {
                                drawRect(
                                    color = Color(0x0AFFFFFF),
                                    topLeft = Offset(x * cellWidth, y * cellHeight),
                                    size = Size(cellWidth, cellHeight),
                                    style = Stroke(width = 0.5f),
                                )
                            }
                        }

                        state.bricks.forEach { brick ->
                            val x = brick.location.x
                            val y = brick.location.y
                            val color = activeBrickColors.getOrElse(brick.colorIndex) { Color(0xFF60A5FA) }

                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x * cellWidth + 1f, y * cellHeight + 1f),
                                size = Size(cellWidth - 2f, cellHeight - 2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f),
                                topLeft = Offset(x * cellWidth + 3f, y * cellHeight + 3f),
                                size = Size(cellWidth / 3f, cellHeight / 3f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
                            )
                        }

                        state.ghostPiece.forEach { point ->
                            val color = activeBrickColors.getOrElse(state.spirit.colorIndex) { Color(0xFF34D399) }
                            drawRoundRect(
                                color = color.copy(alpha = 0.2f),
                                topLeft = Offset(point.x * cellWidth + 1f, point.y * cellHeight + 1f),
                                size = Size(cellWidth - 2f, cellHeight - 2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                style = Stroke(width = 2f)
                            )
                        }

                        state.spirit.location.forEach { point ->
                            val color = activeBrickColors.getOrElse(state.spirit.colorIndex) { Color(0xFF34D399) }

                            drawRoundRect(
                                color = color,
                                topLeft = Offset(point.x * cellWidth + 1f, point.y * cellHeight + 1f),
                                size = Size(cellWidth - 2f, cellHeight - 2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f),
                                topLeft = Offset(point.x * cellWidth + 3f, point.y * cellHeight + 3f),
                                size = Size(cellWidth / 3f, cellHeight / 3f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
                            )
                        }

                        particles.forEach { p ->
                            drawCircle(
                                color = p.color,
                                radius = p.size,
                                center = Offset(size.width * p.xRatio + p.vx * 6f, size.height * p.yRatio + p.vy * 6f)
                            )
                        }

                        // Row-specific Flash Effect
                    if (flashAlpha > 0f && state.clearedIndices.isNotEmpty()) {
                        val cellSize = size.width / state.matrix.first
                        state.clearedIndices.forEach { y ->
                            drawRect(
                                color = Color.White.copy(alpha = flashAlpha),
                                topLeft = Offset(0f, y * cellSize),
                                size = Size(size.width, cellSize)
                            )
                        }
                    }
                    }

                    AnimatedContent(
                        targetState = state.combo,
                        transitionSpec = {
                            (scaleIn(animationSpec = spring(Spring.DampingRatioHighBouncy)) + fadeIn())
                                .togetherWith(scaleOut() + fadeOut())
                        },
                        label = "ComboAnimation"
                    ) { comboCount ->
                        if (comboCount >= 2) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp)
                                    .background(Color(0xE67C3AED), shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "COMBO ×${comboCount - 1}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    if (state.gameStatus == GameStatus.GameOver) {
                        // Game over haptic feedback
                        try {
                            if (state.isHaptic) {
                                HapticPatterns.gameOverPattern(haptic)
                            }
                        } catch (_: Exception) {}
                        
                        val isNewHigh = state.score >= state.highScore && state.score > 0
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xE6090D16)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                if (isNewHigh) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
                                                )
                                            )
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "NEW HIGH SCORE!",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                            Icon(imageVector = Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Text(
                                    text = if (currentMode == GameMode.Blitz && blitzTime <= 0) "TIME'S UP!" else "GAME OVER",
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    letterSpacing = 2.sp
                                )

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "Final Score", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                            Text(text = "${state.score}", color = Color.White, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "Lines Cleared", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                            Text(text = "${state.line}", color = Color(0xFF10B981), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Game Mode", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(imageVector = currentMode.icon, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(14.dp))
                                                Text(text = currentMode.title, color = Color(0xFF22D3EE), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { viewModel.dispatch(Action.Reset) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(48.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "PLAY AGAIN",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Button(
                                    onClick = onExit,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = "MAIN MENU",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    if (state.gameStatus == GameStatus.Onboard) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xD9090D16)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Button(
                                onClick = { viewModel.dispatch(Action.Reset) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                                modifier = Modifier
                                    .size(180.dp, 50.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                        ),
                                        shape = RoundedCornerShape(25.dp)
                                    )
                            ) {
                                Text(
                                    text = "START GAME",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    if (state.gameStatus == GameStatus.Paused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF090D16)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(horizontal = 28.dp)
                            ) {
                                // Title
                                

                                Spacer(modifier = Modifier.height(6.dp))

                                // Resume — Primary CTA (Gradient with Glow)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = Color(0xFF8B5CF6),
                                            spotColor = Color(0xFFEC4899)
                                        )
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { viewModel.dispatch(Action.Resume) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "RESUME",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }

                                // Restart — Secondary CTA
                                Button(
                                    onClick = { viewModel.dispatch(Action.Reset) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "RESTART",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }

                                // Inline Sound & Haptics Toggles
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x14FFFFFF))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Sound Toggle
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.dispatch(Action.Mute) }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isMute) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = if (state.isMute) Color(0xFF94A3B8) else Color(0xFF22D3EE),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = if (state.isMute) "SOUND OFF" else "SOUND ON",
                                            color = if (state.isMute) Color(0xFF94A3B8) else Color(0xFF22D3EE),
                                            fontSize = 9.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    // Divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(32.dp)
                                            .background(Color(0x33FFFFFF))
                                    )

                                    // Haptics Toggle
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.dispatch(Action.HapticToggle) }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isHaptic) Icons.Rounded.Vibration else Icons.Rounded.DoNotDisturbOn,
                                            contentDescription = null,
                                            tint = if (state.isHaptic) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(24.dp).alpha(if (state.isHaptic) 1f else 0.4f)
                                        )
                                        Text(
                                            text = if (state.isHaptic) "HAPTICS ON" else "HAPTICS OFF",
                                            color = if (state.isHaptic) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                                            fontSize = 9.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Quick Controls Reference Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x10FFFFFF))
                                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                            Text(
                                                text = "QUICK CONTROLS",
                                                color = Color(0xFFFBBF24),
                                                fontSize = 10.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Swipe Left / Right", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                            Text(text = "Move Piece", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Swipe Down", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                            Text(text = "Soft Drop", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Tap Board", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                            Text(text = "Rotate", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "↓ Button", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                            Text(text = "Hard Drop", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "HOLD Button", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                            Text(text = "Hold Piece", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Exit — Destructive / De-emphasized Ghost Button
                                Button(
                                    onClick = onExit,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color(0xFF94A3B8)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Text(
                                        text = "EXIT TO MENU",
                                        fontSize = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !state.isPaused,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Main controller buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // D-Pad (Left, Down/Soft Drop, Right) with DAS press/release handling
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GamepadButton(
                                text = "◀",
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(50.dp, 46.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.startMove(Direction.Left) },
                                onRelease = { viewModel.stopMove() }
                            )
                            GamepadButton(
                                text = "▼",
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(50.dp, 46.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.startMove(Direction.Down) },
                                onRelease = { viewModel.stopMove() }
                            )
                            GamepadButton(
                                text = "▶",
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(50.dp, 46.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.startMove(Direction.Right) },
                                onRelease = { viewModel.stopMove() }
                            )
                        }

                        // Action buttons (Hold, Drop, Rotate)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GamepadButton(
                                text = "HOLD",
                                color = Color(0xFF0D9488),
                                modifier = Modifier.size(62.dp, 48.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.dispatch(Action.Hold) }
                            )
                            GamepadButton(
                                text = "DROP",
                                color = Color(0xFFD97706),
                                modifier = Modifier.size(62.dp, 48.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.dispatch(Action.Drop) }
                            )
                            GamepadButton(
                                text = "ROT",
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.size(62.dp, 48.dp),
                                fontSize = 13.sp,
                                onPress = { viewModel.dispatch(Action.Rotate) }
                            )
                        }
                    }

                    // Sub controls panel (Pause / Resume / Start)
                    if (uiState.gameStatus != GameStatus.Paused) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val controlText = when (uiState.gameStatus) {
                                GameStatus.Running -> "PAUSE"
                                GameStatus.Onboard -> "START"
                                else -> "RESUME"
                            }
                            Button(
                                onClick = {
                                    if (uiState.gameStatus == GameStatus.Running) {
                                        viewModel.dispatch(Action.Pause)
                                    } else {
                                        viewModel.dispatch(Action.Resume)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x1AFFFFFF),
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.size(120.dp, 34.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = controlText,
                                    fontSize = 11.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                    letterSpacing = 1.sp
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BlockBlitzScreen(
    gameMode: GameMode = GameMode.Marathon,
    onExit: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null
) {
    val context = LocalContext.current
    val viewModel: BlockBlitzViewModel = viewModel(
        factory = BlockBlitzViewModelFactory(context),
    )
    LaunchedEffect(gameMode) {
        viewModel.startNewGame(gameMode)
    }
    BlockBlitzScreen(
        viewModel = viewModel,
        onExit = onExit,
        onRequestNotificationPermission = onRequestNotificationPermission,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )
}

@Composable
private fun NextPiecePreview(
    spirit: Spirit,
    matrix: Pair<Int, Int>,
    brickColors: List<Color> = BrickColors
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (spirit.shape.isNotEmpty()) {
            val minX = spirit.shape.minOfOrNull { it.x } ?: 0f
            val maxX = spirit.shape.maxOfOrNull { it.x } ?: 0f
            val minY = spirit.shape.minOfOrNull { it.y } ?: 0f
            val maxY = spirit.shape.maxOfOrNull { it.y } ?: 0f

            val shapeWidth = maxX - minX + 1f
            val shapeHeight = maxY - minY + 1f

            val maxDim = maxOf(shapeWidth, shapeHeight, 2f)
            val cellSize = (size.width / (maxDim + 0.4f)).coerceAtMost(size.height / (maxDim + 0.4f))

            val totalW = shapeWidth * cellSize
            val totalH = shapeHeight * cellSize
            val startX = (size.width - totalW) / 2f
            val startY = (size.height - totalH) / 2f

            val color = brickColors.getOrElse(spirit.colorIndex) { Color(0xFFF59E0B) }

            spirit.shape.forEach { point ->
                val drawX = startX + (point.x - minX) * cellSize
                val drawY = startY + (point.y - minY) * cellSize

                drawRoundRect(
                    color = color,
                    topLeft = Offset(drawX + 1f, drawY + 1f),
                    size = Size(cellSize - 2f, cellSize - 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(drawX + 2f, drawY + 2f),
                    size = Size(cellSize / 3f, cellSize / 3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f)
                )
            }
        }
    }
}

@Composable
private fun GamepadButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier.size(54.dp, 48.dp),
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onPress: () -> Unit,
    onRelease: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) color.copy(alpha = 0.8f) else color)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        onPress()
                        waitForUpOrCancellation()
                        isPressed = false
                        onRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black
        )
    }
}

@Composable
private fun CompactStatItem(
    label: String,
    value: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            modifier = Modifier.padding(top = 1.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CompactStatDivider() {
    Box(
        modifier = Modifier
            .size(1.dp, 24.dp)
            .background(Color(0x1AFFFFFF))
    )
}

