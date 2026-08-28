package com.nfrdev.blockblitzhost.blockblitz

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Context.blockBlitzDataStore: DataStore<Preferences> by preferencesDataStore(name = "blockblitz_prefs")

private class BlockBlitzViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BlockBlitzViewModel(
            dataStore = context.applicationContext.blockBlitzDataStore,
            savedStateHandle = SavedStateHandle(),
        ) as T
    }
}

@Composable
fun BlockBlitzScreen(
    viewModel: BlockBlitzViewModel,
    onExit: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = state

    BackHandler {
        when {
            uiState.gameStatus == GameStatus.Running -> viewModel.dispatch(Action.Pause)
            else -> onExit()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Block Blitz",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Button(onClick = onExit) {
                    Text("Exit")
                }
            }

            val matrixWidth = state.matrix.first.toFloat()
            val matrixHeight = state.matrix.second.toFloat()
            val previewSize = 4 to 2
            val previewCell = 18f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Score: ${state.score}")
                        Text("High: ${state.highScore}")
                        Text("Level: ${state.level}")
                        Text("Lines: ${state.line}")
                    }
                }

                Card(
                    modifier = Modifier
                        .size((previewSize.first * previewCell).dp, (previewSize.second * previewCell).dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    NextPiecePreview(
                        spirit = state.spiritNext,
                        matrix = previewSize,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(matrixWidth / matrixHeight)
                    .background(Color(0xFF111827)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellWidth = size.width / matrixWidth
                    val cellHeight = size.height / matrixHeight

                    for (x in 0 until state.matrix.first) {
                        for (y in 0 until state.matrix.second) {
                            drawRect(
                                color = Color(0xFF1F2937),
                                topLeft = Offset(x * cellWidth, y * cellHeight),
                                size = Size(cellWidth, cellHeight),
                                style = Stroke(width = 1f),
                            )
                        }
                    }

                    state.bricks.forEach { brick ->
                        val x = brick.location.x
                        val y = brick.location.y
                        drawRect(
                            color = Color(0xFF60A5FA),
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth, cellHeight),
                        )
                    }

                    state.spirit.location.forEach { point ->
                        drawRect(
                            color = Color(0xFF34D399),
                            topLeft = Offset(point.x * cellWidth, point.y * cellHeight),
                            size = Size(cellWidth, cellHeight),
                        )
                    }
                }

                if (state.gameStatus == GameStatus.GameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Game Over",
                                color = Color.White,
                                fontSize = 28.sp,
                            )
                            Button(
                                onClick = { viewModel.dispatch(Action.Reset) },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text("Play Again")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GameButton("Left") { viewModel.dispatch(Action.Move(Direction.Left)) }
                GameButton("Right") { viewModel.dispatch(Action.Move(Direction.Right)) }
                GameButton("Rotate") { viewModel.dispatch(Action.Rotate) }
                GameButton("Soft Drop") { viewModel.dispatch(Action.Move(Direction.Down)) }
                GameButton("Hard Drop") { viewModel.dispatch(Action.Drop) }
                GameButton(if (uiState.gameStatus == GameStatus.Running) "Pause" else "Resume") {
                    if (uiState.gameStatus == GameStatus.Running) {
                        viewModel.dispatch(Action.Pause)
                    } else {
                        viewModel.dispatch(Action.Resume)
                    }
                }
            }
        }
    }
}

@Composable
fun BlockBlitzScreen(
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: BlockBlitzViewModel = viewModel(
        factory = BlockBlitzViewModelFactory(context),
    )
    BlockBlitzScreen(viewModel = viewModel, onExit = onExit)
}

@Composable
private fun NextPiecePreview(
    spirit: Spirit,
    matrix: Pair<Int, Int>,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cellWidth = size.width / matrix.first
        val cellHeight = size.height / matrix.second

        for (x in 0 until matrix.first) {
            for (y in 0 until matrix.second) {
                drawRect(
                    color = Color(0xFF1F2937),
                    topLeft = Offset(x * cellWidth, y * cellHeight),
                    size = Size(cellWidth, cellHeight),
                    style = Stroke(width = 1f),
                )
            }
        }

        spirit.location.forEach { point ->
            drawRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(point.x * cellWidth, point.y * cellHeight),
                size = Size(cellWidth, cellHeight),
            )
        }
    }
}

@Composable
private fun GameButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(text)
    }
}
