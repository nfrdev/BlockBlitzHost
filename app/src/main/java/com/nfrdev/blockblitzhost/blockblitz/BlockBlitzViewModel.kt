package com.nfrdev.blockblitzhost.blockblitz

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BlockBlitzViewModel(
    private val dataStore: DataStore<Preferences>,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val highScoreKey = intPreferencesKey("blockblitz_high_score")
    private val savedStateKey = "blockblitz_game_state"

    private val initialHighScore: Int = runCatching {
        kotlinx.coroutines.runBlocking {
            dataStore.data.first()[highScoreKey] ?: 0
        }
    }.getOrDefault(0)

    // SavedStateHandle restores the active board, score, spirit, line count, and status.
    private val restoredState: GameEngine.ViewState = savedStateHandle.get<String>(savedStateKey)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString(SerializedViewState.serializer(), raw).toViewState()
            }.getOrNull()
        }
        ?.copy(highScore = initialHighScore)
        ?: GameEngine.ViewState(highScore = initialHighScore)

    private val engine = GameEngine(restoredState)

    private val _uiState = MutableStateFlow(restoredState)
    val uiState: StateFlow<GameEngine.ViewState> = _uiState.asStateFlow()

    init {
        engine.viewState
            .onEach { state ->
                val mergedState = state.copy(highScore = currentHighScore())
                _uiState.value = mergedState
                savedStateHandle[savedStateKey] = Json.encodeToString(
                    SerializedViewState.fromViewState(mergedState)
                )

                val best = currentHighScore()
                val nextBest = maxOf(best, state.score)
                if (nextBest != best) {
                    savedStateHandle["blockblitz_high_score"] = nextBest
                    viewModelScope.launch {
                        dataStore.edit { preferences ->
                            preferences[highScoreKey] = nextBest
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun currentHighScore(): Int {
        return savedStateHandle.get<Int>("blockblitz_high_score")
            ?: runCatching {
                kotlinx.coroutines.runBlocking { dataStore.data.first()[highScoreKey] ?: 0 }
            }.getOrDefault(0)
    }

    fun dispatch(action: Action) {
        engine.dispatch(action)
    }

    @Serializable
    private data class SerializedViewState(
        val bricks: List<SerializedBrick> = emptyList(),
        val score: Int = 0,
        val spirit: SerializedSpirit = SerializedSpirit(),
        val spiritNext: SerializedSpirit = SerializedSpirit(),
        val gameStatus: GameStatus = GameStatus.Onboard,
        val line: Int = 0,
    ) {
        fun toViewState(): GameEngine.ViewState = GameEngine.ViewState(
            bricks = bricks.map { it.toBrick() },
            score = score,
            spirit = spirit.toSpirit(),
            spiritReserve = listOf(spiritNext.toSpirit()),
            gameStatus = gameStatus,
            line = line,
        )

        companion object {
            fun fromViewState(state: GameEngine.ViewState): SerializedViewState = SerializedViewState(
                bricks = state.bricks.map { SerializedBrick.fromBrick(it) },
                score = state.score,
                spirit = SerializedSpirit.fromSpirit(state.spirit),
                spiritNext = SerializedSpirit.fromSpirit(state.spiritNext),
                gameStatus = state.gameStatus,
                line = state.line,
            )
        }
    }

    @Serializable
    private data class SerializedBrick(
        val x: Float,
        val y: Float,
    ) {
        fun toBrick(): Brick = Brick(Point(x, y))

        companion object {
            fun fromBrick(brick: Brick): SerializedBrick = SerializedBrick(
                x = brick.location.x,
                y = brick.location.y,
            )
        }
    }

    @Serializable
    private data class SerializedSpirit(
        val shape: List<SerializedPoint> = emptyList(),
        val offset: SerializedPoint = SerializedPoint(0f, 0f),
    ) {
        fun toSpirit(): Spirit = Spirit(
            shape = shape.map { it.toPoint() },
            offset = offset.toPoint(),
        )

        companion object {
            fun fromSpirit(spirit: Spirit): SerializedSpirit = SerializedSpirit(
                shape = spirit.shape.map { SerializedPoint.fromPoint(it) },
                offset = SerializedPoint.fromPoint(spirit.offset),
            )
        }
    }

    @Serializable
    private data class SerializedPoint(
        val x: Float,
        val y: Float,
    ) {
        fun toPoint(): Point = Point(x, y)

        companion object {
            fun fromPoint(point: Point): SerializedPoint = SerializedPoint(
                x = point.x,
                y = point.y,
            )
        }
    }
}
