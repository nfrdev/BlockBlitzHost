package com.nfrdev.blockblitzhost.blockblitz

import com.nfrdev.blockblitzhost.blockblitz.Spirit.Companion.Empty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min


class GameEngine(initialState: ViewState = ViewState()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _viewState = MutableStateFlow(initialState)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    fun dispatch(action: Action) =
        reduce(viewState.value, action)

    private fun reduce(state: ViewState, action: Action) {
        scope.launch {
            withContext(Dispatchers.Default) {
                emit(when (action) {
                    Action.Reset -> run {
                        if (state.gameStatus == GameStatus.Onboard || state.gameStatus == GameStatus.GameOver)
                            return@run ViewState(
                                gameStatus = GameStatus.Running,
                                isMute = state.isMute
                            )
                        state.copy(gameStatus = GameStatus.ScreenClearing).also {
                            launch {
                                clearScreen(state = state)
                                emit(
                                    ViewState(
                                        gameStatus = GameStatus.Onboard,
                                        isMute = state.isMute
                                    )
                                )
                            }
                        }
                    }

                    Action.Pause -> if (state.isRuning) {
                        state.copy(gameStatus = GameStatus.Paused)
                    } else state

                    Action.Resume -> if (state.isPaused) {
                        state.copy(gameStatus = GameStatus.Running)
                    } else state

                    is Action.Move -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Move)
                        val offset = action.direction.toOffset()
                        val spirit = state.spirit.moveBy(offset)
                        if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            state.copy(spirit = spirit)
                        } else state
                    }

                    Action.Rotate -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Rotate)
                        val spirit = state.spirit.rotate().adjustOffset(state.matrix)
                        if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            state.copy(spirit = spirit)
                        } else state
                    }

                    Action.Drop -> run {
                        if (!state.isRuning) return@run state
                        SoundUtil.play(state.isMute, SoundType.Drop)
                        var i = 0
                        while (state.spirit.moveBy(0 to ++i).isValidInMatrix(state.bricks, state.matrix)) {
                        }
                        val spirit = state.spirit.moveBy(0 to i - 1)
                        state.copy(spirit = spirit)
                    }

                    Action.GameTick -> run {
                        if (!state.isRuning) return@run state

                        if (state.spirit != Empty) {
                            val spirit = state.spirit.moveBy(Direction.Down.toOffset())
                            if (spirit.isValidInMatrix(state.bricks, state.matrix)) {
                                return@run state.copy(spirit = spirit)
                            }
                        }

                        if (!state.spirit.isValidInMatrix(state.bricks, state.matrix)) {
                            return@run state.copy(gameStatus = GameStatus.ScreenClearing).also {
                                launch {
                                    emit(clearScreen(state = state).copy(gameStatus = GameStatus.GameOver))
                                }
                            }
                        }

                        val (updatedBricks, clearedLines) = updateBricks(
                            state.bricks,
                            state.spirit,
                            matrix = state.matrix
                        )
                        val (noClear, clearing, cleared) = updatedBricks
                        val newState = state.copy(
                            spirit = state.spiritNext,
                            spiritReserve = (state.spiritReserve - state.spiritNext).takeIf { it.isNotEmpty() }
                                ?: generateSpiritReverse(state.matrix),
                            score = state.score + calculateScore(clearedLines) +
                                    if (state.spirit != Empty) ScoreEverySpirit else 0,
                            line = state.line + clearedLines
                        )
                        if (clearedLines != 0) {
                            SoundUtil.play(state.isMute, SoundType.Clean)
                            state.copy(gameStatus = GameStatus.LineClearing).also {
                                launch {
                                    repeat(5) {
                                        emit(
                                            state.copy(
                                                gameStatus = GameStatus.LineClearing,
                                                spirit = Empty,
                                                bricks = if (it % 2 == 0) noClear else clearing
                                            )
                                        )
                                        delay(100)
                                    }
                                    emit(
                                        newState.copy(
                                            bricks = cleared,
                                            gameStatus = GameStatus.Running
                                        )
                                    )
                                }
                            }
                        } else {
                            newState.copy(bricks = noClear)
                        }
                    }

                    Action.Mute -> state.copy(isMute = !state.isMute)
                })
            }
        }
    }

    private suspend fun clearScreen(state: ViewState): ViewState {
        SoundUtil.play(state.isMute, SoundType.Start)
        val xRange = 0 until state.matrix.first
        var newState = state

        (state.matrix.second downTo 0).forEach { y ->
            emit(
                state.copy(
                    gameStatus = GameStatus.ScreenClearing,
                    bricks = state.bricks + Brick.of(xRange, y until state.matrix.second)
                )
            )
            delay(50)
        }
        (0..state.matrix.second).forEach { y ->
            emit(
                state.copy(
                    gameStatus = GameStatus.ScreenClearing,
                    bricks = Brick.of(xRange, y until state.matrix.second),
                    spirit = Empty
                ).also { newState = it }
            )
            delay(50)
        }
        return newState
    }

    private fun emit(state: ViewState) {
        _viewState.value = state
    }

    /**
     * Return a [Triple] to store clear-info for bricks:
     * - [Triple.first]:  Bricks before line clearing (Current bricks plus Spirit)
     * - [Triple.second]: Bricks after line cleared but not offset (bricks minus lines should be cleared)
     * - [Triple.third]: Bricks after line cleared (after bricks offset)
     */
    private fun updateBricks(
        curBricks: List<Brick>,
        spirit: Spirit,
        matrix: Pair<Int, Int>
    ): Pair<Triple<List<Brick>, List<Brick>, List<Brick>>, Int> {
        val bricks = (curBricks + Brick.of(spirit))
        val map = mutableMapOf<Float, MutableSet<Float>>()
        bricks.forEach {
            map.getOrPut(it.location.y) {
                mutableSetOf()
            }.add(it.location.x)
        }
        var clearing = bricks
        var cleared = bricks
        val clearLines = map.entries.sortedBy { it.key }
            .filter { it.value.size == matrix.first }.map { it.key }
            .onEach { line ->
                clearing = clearing.filter { it.location.y != line }
                cleared = cleared.filter { it.location.y != line }
                    .map { if (it.location.y < line) it.offsetBy(0 to 1) else it }
            }

        return Triple(bricks, clearing, cleared) to clearLines.size
    }

    data class ViewState(
        val bricks: List<Brick> = emptyList(),
        val spirit: Spirit = Empty,
        val spiritReserve: List<Spirit> = emptyList(),
        val matrix: Pair<Int, Int> = MatrixWidth to MatrixHeight,
        val gameStatus: GameStatus = GameStatus.Onboard,
        val score: Int = 0,
        val line: Int = 0,
        val isMute: Boolean = false,
    ) {
        val level: Int
            get() = min(10, 1 + line / 20)

        val spiritNext: Spirit
            get() = spiritReserve.firstOrNull() ?: Empty

        val isPaused
            get() = gameStatus == GameStatus.Paused

        val isRuning
            get() = gameStatus == GameStatus.Running
    }
}

@Deprecated("Use GameEngine instead.")
typealias GameViewModel = GameEngine

sealed interface Action {
    data class Move(val direction: Direction) : Action
    object Reset : Action
    object Pause : Action
    object Resume : Action
    object Rotate : Action
    object Drop : Action
    object GameTick : Action
    object Mute : Action
}

enum class GameStatus {
    Onboard, //游戏欢迎页
    Running, //游戏进行中
    LineClearing,// 消行动画中
    Paused,//游戏暂停
    ScreenClearing, //清屏动画中
    GameOver//游戏结束
}


private const val MatrixWidth = 12
private const val MatrixHeight = 24
