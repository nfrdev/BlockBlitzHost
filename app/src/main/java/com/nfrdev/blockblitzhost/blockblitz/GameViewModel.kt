package com.nfrdev.blockblitzhost.blockblitz

import com.nfrdev.blockblitzhost.blockblitz.Spirit.Companion.Empty
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlinx.serialization.Transient

class GameEngine(initialState: ViewState = ViewState()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _viewState = MutableStateFlow(initialState)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    // Sequential action queue to guarantee single-threaded state transitions
    private val actionChannel = Channel<Action>(Channel.UNLIMITED)
    private var animationJob: Job? = null
    private var lockDelayJob: Job? = null

    init {
        scope.launch {
            for (action in actionChannel) {
                processAction(action)
            }
        }
    }

    fun dispatch(action: Action) {
        actionChannel.trySend(action)
    }

    fun cancel() {
        scope.cancel()
    }

    private fun startLockDelayTimer() {
        lockDelayJob = scope.launch {
            delay(500)
            dispatch(Action.LockPiece)
        }
    }

    private fun adjustLockDelay(newState: ViewState, action: Action): ViewState {
        if (!newState.isRunning || newState.spirit == Empty) {
            lockDelayJob?.cancel()
            lockDelayJob = null
            return newState.copy(lockDelayResets = 0)
        }

        val blockSet = newState.blockSet
        val canMoveDown = newState.spirit.moveBy(Direction.Down.toOffset()).isValidInMatrix(blockSet, newState.matrix)

        if (canMoveDown) {
            lockDelayJob?.cancel()
            lockDelayJob = null
            return newState.copy(lockDelayResets = 0)
        } else {
            // It is on the ground!
            val isManualMovement = action is Action.Move || action == Action.Rotate
            if (lockDelayJob == null) {
                // Just landed
                startLockDelayTimer()
                return newState
            } else if (isManualMovement) {
                // Already on ground, and player moved/rotated
                if (newState.lockDelayResets < 15) {
                    lockDelayJob?.cancel()
                    startLockDelayTimer()
                    return newState.copy(lockDelayResets = newState.lockDelayResets + 1)
                }
            }
            return newState
        }
    }

    private suspend fun processAction(action: Action) {
        val state = _viewState.value
        val blockSet = state.blockSet

        var nextState = state

        when (action) {
            Action.Reset -> {
                animationJob?.cancel()
                lockDelayJob?.cancel()
                lockDelayJob = null
                val bag = generate7Bag(state.matrix)
                nextState = ViewState(
                    gameStatus = GameStatus.Running,
                    spirit = bag.first(),
                    spiritReserve = bag.drop(1),
                    isMute = state.isMute,
                    highScore = state.highScore
                )
                emit(nextState)
            }

            is Action.ResetWithPrefs -> {
                animationJob?.cancel()
                lockDelayJob?.cancel()
                lockDelayJob = null
                val bag = generate7Bag(state.matrix)
                nextState = ViewState(
                    gameStatus = GameStatus.Running,
                    spirit = bag.first(),
                    spiritReserve = bag.drop(1),
                    isMute = action.isMute,
                    isHaptic = action.isHaptic,
                    highScore = state.highScore
                )
                emit(nextState)
            }

            Action.Pause -> {
                if (state.isRunning) {
                    lockDelayJob?.cancel()
                    lockDelayJob = null
                    nextState = state.copy(gameStatus = GameStatus.Paused)
                    emit(nextState)
                }
            }

            Action.GameOver -> {
                lockDelayJob?.cancel()
                lockDelayJob = null
                animationJob?.cancel()
                nextState = state.copy(gameStatus = GameStatus.GameOver, spirit = Empty)
                emit(nextState)
            }

            Action.Resume -> {
                if (state.isPaused || state.gameStatus == GameStatus.Onboard) {
                    if (state.gameStatus == GameStatus.Onboard) {
                        val bag = generate7Bag(state.matrix)
                        nextState = state.copy(
                            gameStatus = GameStatus.Running,
                            spirit = bag.first(),
                            spiritReserve = bag.drop(1)
                        )
                    } else {
                        nextState = state.copy(gameStatus = GameStatus.Running)
                    }
                    emit(nextState)
                    nextState = adjustLockDelay(nextState, action)
                }
            }

            is Action.Move -> {
                if (!state.isRunning) return
                val offset = action.direction.toOffset()
                val movedSpirit = state.spirit.moveBy(offset)
                if (movedSpirit.isValidInMatrix(blockSet, state.matrix)) {
                    val softDropBonus = if (action.direction == Direction.Down) 1 else 0
                    SoundUtil.play(state.isMute, SoundType.Move)
                    nextState = state.copy(
                        spirit = movedSpirit,
                        score = state.score + softDropBonus,
                        lastActionWasRotation = false
                    )
                    emit(nextState)
                    nextState = adjustLockDelay(nextState, action)
                }
            }

            Action.Rotate -> {
                if (!state.isRunning) return
                val rotated = state.spirit.tryRotate(blockSet, state.matrix)
                if (rotated != null) {
                    SoundUtil.play(state.isMute, SoundType.Rotate)
                    nextState = state.copy(
                        spirit = rotated,
                        lastActionWasRotation = true
                    )
                    emit(nextState)
                    nextState = adjustLockDelay(nextState, action)
                }
            }

            Action.Drop -> {
                // Hard Drop: Drops and immediately locks
                if (!state.isRunning) return
                SoundUtil.play(state.isMute, SoundType.Drop)
                lockDelayJob?.cancel()
                lockDelayJob = null

                var dropDistance = 0
                while (state.spirit.moveBy(0 to (dropDistance + 1)).isValidInMatrix(blockSet, state.matrix)) {
                    dropDistance++
                }
                val droppedSpirit = state.spirit.moveBy(0 to dropDistance)
                val hardDropBonus = dropDistance * 2
                lockPieceAndAdvance(state.copy(spirit = droppedSpirit, score = state.score + hardDropBonus))
            }

            Action.GameTick -> {
                if (!state.isRunning) return
                if (state.spirit != Empty) {
                    val movedDown = state.spirit.moveBy(Direction.Down.toOffset())
                    if (movedDown.isValidInMatrix(blockSet, state.matrix)) {
                        nextState = state.copy(
                            spirit = movedDown,
                            lastActionWasRotation = false
                        )
                        emit(nextState)
                        nextState = adjustLockDelay(nextState, action)
                        return
                    }
                }
                nextState = adjustLockDelay(state, action)
            }

            Action.Hold -> {
                if (!state.isRunning || state.hasHeld) return
                lockDelayJob?.cancel()
                lockDelayJob = null

                val currentSpiritBase = state.spirit.toSpawnState(state.matrix)
                val nextSpirit: Spirit
                val nextReserve: List<Spirit>
                val nextHeld: Spirit = currentSpiritBase

                if (state.heldSpirit == Empty) {
                    var reserve = state.spiritReserve
                    if (reserve.isEmpty()) {
                        reserve = generate7Bag(state.matrix)
                    }
                    nextSpirit = reserve.first()
                    nextReserve = reserve.drop(1).ifEmpty { generate7Bag(state.matrix) }
                } else {
                    nextSpirit = state.heldSpirit
                    nextReserve = state.spiritReserve
                }

                if (!nextSpirit.isValidInMatrix(blockSet, state.matrix)) {
                    nextState = state.copy(
                        gameStatus = GameStatus.GameOver,
                        spirit = Empty,
                        heldSpirit = nextHeld,
                        hasHeld = true
                    )
                    emit(nextState)
                    return
                }

                nextState = state.copy(
                    spirit = nextSpirit,
                    spiritReserve = nextReserve,
                    heldSpirit = nextHeld,
                    hasHeld = true,
                    lockDelayResets = 0,
                    lastActionWasRotation = false
                )
                emit(nextState)
                nextState = adjustLockDelay(nextState, action)
            }

            Action.LockPiece -> {
                if (!state.isRunning) return
                if (state.spirit != Empty) {
                    if (state.spirit.moveBy(Direction.Down.toOffset()).isValidInMatrix(blockSet, state.matrix)) {
                        return
                    }
                }
                lockPieceAndAdvance(state)
            }

            Action.Mute -> {
                nextState = state.copy(isMute = !state.isMute)
                emit(nextState)
            }

            Action.HapticToggle -> {
                nextState = state.copy(isHaptic = !state.isHaptic)
                emit(nextState)
            }
        }
    }

    private fun lockPieceAndAdvance(state: ViewState) {
        lockDelayJob?.cancel()
        lockDelayJob = null

        val blockSet = state.blockSet

        // Top-Out / Lock-Out Game Over check
        if (!state.spirit.isValidInMatrix(blockSet, state.matrix) || state.spirit.location.any { it.y < 0 }) {
            emit(state.copy(gameStatus = GameStatus.GameOver, spirit = Empty))
            return
        }

        // T-Spin Corner check
        var isTSpin = false
        var isMiniTSpin = false
        if (state.lastActionWasRotation && state.spirit.pieceType == PieceType.T) {
            val cx = state.spirit.offset.x.toInt()
            val cy = state.spirit.offset.y.toInt()

            fun isOccupied(x: Int, y: Int): Boolean {
                return x < 0 || x >= state.matrix.first || y >= state.matrix.second || y < 0 || blockSet.contains(x to y)
            }

            val tl = isOccupied(cx - 1, cy - 1)
            val tr = isOccupied(cx + 1, cy - 1)
            val bl = isOccupied(cx - 1, cy + 1)
            val br = isOccupied(cx + 1, cy + 1)

            val totalOccupied = (if (tl) 1 else 0) + (if (tr) 1 else 0) + (if (bl) 1 else 0) + (if (br) 1 else 0)
            if (totalOccupied >= 3) {
                val pointed1: Boolean
                val pointed2: Boolean
                when (state.spirit.rotationState) {
                    0 -> { // Pointing up
                        pointed1 = tl
                        pointed2 = tr
                    }
                    1 -> { // Pointing right
                        pointed1 = tr
                        pointed2 = br
                    }
                    2 -> { // Pointing down
                        pointed1 = bl
                        pointed2 = br
                    }
                    3 -> { // Pointing left
                        pointed1 = tl
                        pointed2 = bl
                    }
                    else -> {
                        pointed1 = false
                        pointed2 = false
                    }
                }
                if (pointed1 && pointed2) {
                    isTSpin = true
                } else {
                    isMiniTSpin = true
                }
            }
        }

        val (updatedBricks, fullRowYs) = calculateLineClears(state.bricks, state.spirit, state.matrix)
        val clearedLines = fullRowYs.size
        val (noClear, clearing, cleared) = updatedBricks

        var reserve = state.spiritReserve
        if (reserve.isEmpty()) {
            reserve = generate7Bag(state.matrix)
        }
        val nextSpirit = reserve.first()
        val nextReserve = reserve.drop(1).ifEmpty { generate7Bag(state.matrix) }

        // Combo and Back-to-Back update
        val newCombo = if (clearedLines > 0) state.combo + 1 else 0
        val isDifficult = clearedLines == 4 || ((isTSpin || isMiniTSpin) && clearedLines > 0)
        val newB2B = if (clearedLines > 0) isDifficult else state.backToBack

        // Scoring calculation
        val linePoints = calculateScore(
            lines = clearedLines,
            isTSpin = isTSpin,
            isMiniTSpin = isMiniTSpin,
            backToBack = state.backToBack
        ) * state.level

        val comboPoints = if (clearedLines > 0 && newCombo >= 2) {
            50 * (newCombo - 1) * state.level
        } else {
            0
        }

        val piecePoints = if (state.spirit != Empty) ScoreEverySpirit else 0
        val earnedPoints = linePoints + comboPoints + piecePoints
        val newScore = state.score + earnedPoints
        val newLines = state.line + clearedLines

        val nextSpawnBlockSet = cleared.map { it.location.x.toInt() to it.location.y.toInt() }.toSet()
        val nextSpawnValid = nextSpirit.isValidInMatrix(nextSpawnBlockSet, state.matrix)

        if (clearedLines > 0) {
            SoundUtil.play(state.isMute, SoundType.Clean)
            val newTSpinCount = if (isTSpin) state.tSpinCount + 1 else state.tSpinCount
            emit(
                state.copy(
                    gameStatus = GameStatus.LineClearing,
                    bricks = clearing,
                    spirit = Empty,
                    scorePopAmount = earnedPoints,
                    scorePopCounter = state.scorePopCounter + 1,
                    tSpinCount = newTSpinCount,
                    clearedIndices = fullRowYs
                )
            )

            animationJob?.cancel()
            animationJob = scope.launch {
                delay(200)

                if (_viewState.value.gameStatus != GameStatus.LineClearing) return@launch

                if (!nextSpawnValid) {
                    emit(
                        state.copy(
                            bricks = cleared,
                            score = newScore,
                            line = newLines,
                            gameStatus = GameStatus.GameOver,
                            spirit = Empty,
                            combo = newCombo,
                            backToBack = newB2B,
                            tSpinCount = state.tSpinCount,
                            clearedIndices = emptySet()
                        )
                    )
                } else {
                    emit(
                        state.copy(
                            bricks = cleared,
                            spirit = nextSpirit,
                            spiritReserve = nextReserve,
                            score = newScore,
                            line = newLines,
                            gameStatus = GameStatus.Running,
                            hasHeld = false,
                            lockDelayResets = 0,
                            lastActionWasRotation = false,
                            combo = newCombo,
                            backToBack = newB2B,
                            clearedIndices = emptySet()
                        )
                    )
                }
            }
        } else {
            val noClearBlockSet = noClear.map { it.location.x.toInt() to it.location.y.toInt() }.toSet()
            if (!nextSpirit.isValidInMatrix(noClearBlockSet, state.matrix)) {
                emit(
                    state.copy(
                        bricks = noClear,
                        score = newScore,
                        gameStatus = GameStatus.GameOver,
                        spirit = Empty,
                        combo = newCombo,
                        backToBack = newB2B,
                        scorePopAmount = earnedPoints,
                        scorePopCounter = state.scorePopCounter + 1
                    )
                )
            } else {
                emit(
                    state.copy(
                        bricks = noClear,
                        spirit = nextSpirit,
                        spiritReserve = nextReserve,
                        score = newScore,
                        line = newLines,
                        gameStatus = GameStatus.Running,
                        hasHeld = false,
                        lockDelayResets = 0,
                        lastActionWasRotation = false,
                        combo = newCombo,
                        backToBack = newB2B,
                        scorePopAmount = earnedPoints,
                        scorePopCounter = state.scorePopCounter + 1
                    )
                )
            }
        }
    }

    private fun calculateLineClears(
        curBricks: List<Brick>,
        spirit: Spirit,
        matrix: Pair<Int, Int>
    ): Pair<Triple<List<Brick>, List<Brick>, List<Brick>>, Set<Int>> {
        val allBricks = curBricks + Brick.of(spirit)
        val rowCounts = mutableMapOf<Int, Int>()
        allBricks.forEach {
            val y = it.location.y.toInt()
            rowCounts[y] = (rowCounts[y] ?: 0) + 1
        }

        val fullRowYs = rowCounts.filter { it.value >= matrix.first }.keys.toSet()
        val clearing = allBricks.filter { it.location.y.toInt() !in fullRowYs }
        val cleared = clearing.map { brick ->
            val shift = fullRowYs.count { clearY -> clearY > brick.location.y.toInt() }
            brick.offsetBy(0 to shift)
        }

        return Triple(allBricks, clearing, cleared) to fullRowYs
    }

    private fun emit(state: ViewState) {
        _viewState.value = state.withDerived()
    }

    data class ViewState(
        val bricks: List<Brick> = emptyList(),
        val spirit: Spirit = Empty,
        val spiritReserve: List<Spirit> = emptyList(),
        val matrix: Pair<Int, Int> = MatrixWidth to MatrixHeight,
        val gameStatus: GameStatus = GameStatus.Onboard,
        val score: Int = 0,
        val highScore: Int = 0,
        val line: Int = 0,
        val isMute: Boolean = false,
        val isHaptic: Boolean = true,
        val heldSpirit: Spirit = Empty,
        val hasHeld: Boolean = false,
        val combo: Int = 0,
        val backToBack: Boolean = false,
        val lockDelayResets: Int = 0,
        val lastActionWasRotation: Boolean = false,
        val scorePopAmount: Int = 0,
        val scorePopCounter: Int = 0,
        val tSpinCount: Int = 0,
        val clearedIndices: Set<Int> = emptySet(),
        val cachedBlockSet: Set<Pair<Int, Int>>? = null,
        val cachedGhostPiece: List<Point>? = null,
    ) {
        val level: Int get() = min(10, 1 + line / 10)
        val spiritNext: Spirit get() = spiritReserve.firstOrNull() ?: Empty
        val isPaused get() = gameStatus == GameStatus.Paused
        val isRunning get() = gameStatus == GameStatus.Running

        val blockSet: Set<Pair<Int, Int>>
            get() = cachedBlockSet ?: bricks.map { it.location.x.toInt() to it.location.y.toInt() }.toSet()

        val ghostPiece: List<Point>
            get() = cachedGhostPiece ?: run {
                if (spirit == Empty || !isRunning) emptyList()
                else {
                    val bs = blockSet
                    var dropDistance = 0
                    while (spirit.moveBy(0 to (dropDistance + 1)).isValidInMatrix(bs, matrix)) {
                        dropDistance++
                    }
                    spirit.moveBy(0 to dropDistance).location
                }
            }

        fun withDerived(): ViewState {
            val bs = blockSet
            val gp = ghostPiece
            return copy(cachedBlockSet = bs, cachedGhostPiece = gp)
        }
    }
}

@Deprecated("Use GameEngine instead.")
typealias GameViewModel = GameEngine

sealed interface Action {
    data class Move(val direction: Direction) : Action
    data object Reset : Action
    data class ResetWithPrefs(val isMute: Boolean, val isHaptic: Boolean) : Action
    data object Pause : Action
    data object Resume : Action
    data object Rotate : Action
    data object Drop : Action
    data object GameTick : Action
    data object Mute : Action
    data object HapticToggle : Action
    data object Hold : Action
    data object LockPiece : Action
    data object GameOver : Action
}

enum class GameStatus {
    Onboard, Running, LineClearing, Paused, GameOver
}

private const val MatrixWidth = 10
private const val MatrixHeight = 20
