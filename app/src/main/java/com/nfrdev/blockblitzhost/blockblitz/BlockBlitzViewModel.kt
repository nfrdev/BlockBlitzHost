package com.nfrdev.blockblitzhost.blockblitz

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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

import androidx.datastore.preferences.core.stringPreferencesKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nfrdev.blockblitzhost.notifications.InactivityWorker
import com.nfrdev.blockblitzhost.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class BlockBlitzViewModel(
    private val application: Application,
    private val dataStore: DataStore<Preferences>,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val highScoreKey = intPreferencesKey("blockblitz_high_score")
    private val gamesPlayedKey = intPreferencesKey("blockblitz_games_played")
    private val linesClearedKey = intPreferencesKey("blockblitz_lines_cleared")
    private val soundMutedKey = androidx.datastore.preferences.core.booleanPreferencesKey("blockblitz_sound_muted")
    private val leaderboardKey = stringPreferencesKey("blockblitz_leaderboard")
    private val themeKey = stringPreferencesKey("blockblitz_theme")
    private val hapticEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("blockblitz_haptic_enabled")
    private val notificationsEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("blockblitz_notifications_enabled")
    private val savedStateKey = "blockblitz_game_state"

    // Restore state from SavedStateHandle across process death / recreation
    private val restoredState: GameEngine.ViewState = savedStateHandle.get<String>(savedStateKey)
        ?.let { raw ->
            runCatching {
                Json.decodeFromString<SerializedViewState>(raw).toViewState()
            }.getOrNull()
        } ?: GameEngine.ViewState()

    private val engine = GameEngine(restoredState)
    private val _uiState = MutableStateFlow(restoredState)
    val uiState: StateFlow<GameEngine.ViewState> = _uiState.asStateFlow()

    private val _gameMode = MutableStateFlow(GameMode.Marathon)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _blitzTimeRemaining = MutableStateFlow(120)
    val blitzTimeRemaining: StateFlow<Int> = _blitzTimeRemaining.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardEntry>> = _leaderboard.asStateFlow()

    private val _activeTheme = MutableStateFlow(GameTheme.Cyberpunk)
    val activeTheme: StateFlow<GameTheme> = _activeTheme.asStateFlow()

    private val _dailyChallengeResetTime = MutableStateFlow(0L)
    val dailyChallengeResetTime: StateFlow<Long> = _dailyChallengeResetTime.asStateFlow()

    private var unlockedAchievements = setOf<String>()
    private var paused = false

    fun setGameMode(mode: GameMode) {
        _gameMode.value = mode
        when (mode) {
            GameMode.Blitz -> _blitzTimeRemaining.value = 120
            GameMode.DailyChallenge -> {
                _blitzTimeRemaining.value = 120
                updateDailyChallengeResetTime()
            }
            else -> {}
        }
    }

    private fun updateDailyChallengeResetTime() {
        val now = System.currentTimeMillis()
        val secondsInDay = 86400000L
        val dayStart = (now / secondsInDay) * secondsInDay
        val nextDayStart = dayStart + secondsInDay
        val secondsUntilReset = (nextDayStart - now) / 1000
        _dailyChallengeResetTime.value = secondsUntilReset
    }

    fun setTheme(theme: GameTheme) {
        _activeTheme.value = theme
        viewModelScope.launch {
            dataStore.edit { it[themeKey] = theme.name }
        }
    }

    fun startNewGame(mode: GameMode) {
        setGameMode(mode)
        dispatch(Action.Reset)
    }

    private var moveJob: kotlinx.coroutines.Job? = null

    fun startMove(direction: Direction) {
        if (!uiState.value.isRunning) return
        dispatch(Action.Move(direction))
        moveJob?.cancel()
        moveJob = viewModelScope.launch {
            delay(150) // DAS (Delayed Auto-Shift)
            while (true) {
                dispatch(Action.Move(direction))
                delay(30) // ARR (Auto-Repeat Rate)
            }
        }
    }

    fun stopMove() {
        moveJob?.cancel()
        moveJob = null
    }

    private fun detectAchievements(state: GameEngine.ViewState, prevStatus: GameStatus): Set<String> {
        val newly = mutableSetOf<String>()
        
        // First game achievement
        if (prevStatus != GameStatus.Running && state.gameStatus == GameStatus.Running) {
            val total = (_leaderboard.value.size + if (state.score > 0) 1 else 0)
            if (total == 1) newly.add("first_game")
        }
        
        // Game Over achievements
        if (state.gameStatus == GameStatus.GameOver && prevStatus != GameStatus.GameOver) {
            if (state.score >= 1000) newly.add("first_1000")
            if (state.tSpinCount >= 5) newly.add("tspin_master")
            
            when (_gameMode.value) {
                GameMode.Blitz -> if (state.score >= 5000) newly.add("blitz_hero")
                GameMode.Marathon -> if (state.line >= 40) newly.add("line_breaker")
                GameMode.Zen -> if (state.score >= 2000) newly.add("zen_master")
                else -> {}
            }
            
            if (state.combo >= 10) newly.add("combo_king")
            if (!paused) newly.add("no_pause")

            // Trigger notifications for new achievements
            viewModelScope.launch {
                val enabled = dataStore.data.first()[notificationsEnabledKey] ?: true
                if (enabled) {
                    newly.forEach { id ->
                        AchievementList.getAchievement(id)?.let { ach ->
                            NotificationHelper.showAchievementNotification(application, ach.name, ach.icon)
                        }
                    }
                }
            }
            
            // Reset inactivity timer on Game Over
            scheduleInactivityReminder()
        }
        
        return newly
    }

    private fun scheduleInactivityReminder() {
        viewModelScope.launch {
            val enabled = dataStore.data.first()[notificationsEnabledKey] ?: true
            if (!enabled) return@launch

            val inactivityRequest = OneTimeWorkRequestBuilder<InactivityWorker>()
                .setInitialDelay(3, TimeUnit.DAYS)
                .addTag("inactivity_reminder")
                .build()

            WorkManager.getInstance(application).enqueueUniqueWork(
                "inactivity_reminder",
                ExistingWorkPolicy.REPLACE,
                inactivityRequest
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMove()
        engine.cancel()
    }

    init {
        var persistedHighScore = 0
        // Preferences & stats loading
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            persistedHighScore = prefs[highScoreKey] ?: 0
            val isMuted = prefs[soundMutedKey] ?: false
            val isHaptic = prefs[hapticEnabledKey] ?: true
            val rawLeaderboard = prefs[leaderboardKey]
            if (rawLeaderboard != null) {
                runCatching {
                    _leaderboard.value = Json.decodeFromString<List<LeaderboardEntry>>(rawLeaderboard)
                }
            }
            val rawTheme = prefs[themeKey]
            if (rawTheme != null) {
                _activeTheme.value = runCatching { GameTheme.valueOf(rawTheme) }.getOrDefault(GameTheme.Cyberpunk)
            }
            _uiState.value = _uiState.value.copy(
                highScore = persistedHighScore,
                isMute = isMuted,
                isHaptic = isHaptic
            )
            // Synchronize engine state
            engine.dispatch(Action.Reset) // Trigger initial state update with correct prefs
        }

        var lastSavedStatus = restoredState.gameStatus
        var lastSavedLines = restoredState.line

        engine.viewState
            .onEach { state ->
                val currentHigh = maxOf(persistedHighScore, state.score)
                _uiState.value = state.copy(highScore = currentHigh)

                // Track pause state
                paused = state.isPaused

                // Track games played
                if (state.gameStatus == GameStatus.Running && lastSavedStatus != GameStatus.Running && lastSavedStatus != GameStatus.Paused) {
                    viewModelScope.launch {
                        dataStore.edit { preferences ->
                            val currentPlayed = preferences[gamesPlayedKey] ?: 0
                            preferences[gamesPlayedKey] = currentPlayed + 1
                        }
                    }
                }

                // Track lines cleared
                if (state.line > lastSavedLines) {
                    val clearedDelta = state.line - lastSavedLines
                    viewModelScope.launch {
                        dataStore.edit { preferences ->
                            val currentLines = preferences[linesClearedKey] ?: 0
                            preferences[linesClearedKey] = currentLines + clearedDelta
                        }
                    }
                }

                // Handle Game Over: record leaderboard entry
                if (state.gameStatus == GameStatus.GameOver && lastSavedStatus != GameStatus.GameOver && state.score > 0) {
                    viewModelScope.launch {
                        val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())
                        val newAchievements = detectAchievements(state, lastSavedStatus)
                        unlockedAchievements = unlockedAchievements.union(newAchievements)
                        val newEntry = LeaderboardEntry(
                            score = state.score,
                            mode = _gameMode.value.title,
                            lines = state.line,
                            dateFormatted = dateStr,
                            achievements = newAchievements
                        )
                        val updated = (_leaderboard.value + newEntry)
                            .sortedByDescending { it.score }
                            .take(5)
                        _leaderboard.value = updated
                        dataStore.edit { it[leaderboardKey] = Json.encodeToString(updated) }
                    }
                }

                // Event-driven persistence: Only serialize on discrete game milestones
                val shouldPersist = state.gameStatus != lastSavedStatus ||
                        state.line != lastSavedLines ||
                        state.gameStatus == GameStatus.GameOver ||
                        state.gameStatus == GameStatus.Paused

                if (shouldPersist) {
                    lastSavedStatus = state.gameStatus
                    lastSavedLines = state.line
                    savedStateHandle[savedStateKey] = Json.encodeToString(
                        SerializedViewState.fromViewState(state)
                    )
                }

                if (state.score > persistedHighScore) {
                    persistedHighScore = state.score
                    viewModelScope.launch {
                        dataStore.edit { it[highScoreKey] = state.score }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Gravity Timer Loop with GameMode behavior
        viewModelScope.launch {
            while (true) {
                val state = engine.viewState.value
                val speed = when (_gameMode.value) {
                    GameMode.Zen -> 650L
                    GameMode.Blitz -> 420L
                    GameMode.Marathon -> (800 - (state.level - 1) * 70).coerceAtLeast(100).toLong()
                    GameMode.DailyChallenge -> 500L
                }
                delay(speed)
                if (engine.viewState.value.isRunning) {
                    engine.dispatch(Action.GameTick)
                }
            }
        }

        // Blitz 2-Minute Countdown Loop
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_gameMode.value == GameMode.Blitz && engine.viewState.value.isRunning) {
                    if (_blitzTimeRemaining.value > 0) {
                        _blitzTimeRemaining.value -= 1
                        if (_blitzTimeRemaining.value == 0) {
                            engine.dispatch(Action.GameOver)
                        }
                    }
                }
            }
        }
    }

    fun dispatch(action: Action) {
        if (action == Action.Reset) {
            _blitzTimeRemaining.value = 120
        }
        if (action == Action.Mute) {
            viewModelScope.launch {
                dataStore.edit { it[soundMutedKey] = !uiState.value.isMute }
            }
        }
        if (action == Action.HapticToggle) {
            viewModelScope.launch {
                dataStore.edit { it[hapticEnabledKey] = !uiState.value.isHaptic }
            }
        }
        engine.dispatch(action)
    }

    @Serializable
    private data class SerializedViewState(
        val bricks: List<SerializedBrick> = emptyList(),
        val score: Int = 0,
        val spirit: SerializedSpirit = SerializedSpirit(),
        val spiritNext: SerializedSpirit? = null,
        val spiritReserve: List<SerializedSpirit> = emptyList(),
        val gameStatus: GameStatus = GameStatus.Onboard,
        val line: Int = 0,
        val heldSpirit: SerializedSpirit = SerializedSpirit(),
        val hasHeld: Boolean = false,
        val combo: Int = 0,
        val backToBack: Boolean = false,
        val tSpinCount: Int = 0,
        val clearedIndices: Set<Int> = emptySet(),
    ) {
        fun toViewState(): GameEngine.ViewState = GameEngine.ViewState(
            bricks = bricks.map { it.toBrick() },
            score = score,
            spirit = spirit.toSpirit(),
            spiritReserve = spiritReserve.map { it.toSpirit() }.ifEmpty {
                listOfNotNull(spiritNext?.toSpirit())
            },
            gameStatus = gameStatus,
            line = line,
            heldSpirit = heldSpirit.toSpirit(),
            hasHeld = hasHeld,
            combo = combo,
            backToBack = backToBack,
            tSpinCount = tSpinCount,
            clearedIndices = clearedIndices
        )

        companion object {
            fun fromViewState(state: GameEngine.ViewState): SerializedViewState = SerializedViewState(
                bricks = state.bricks.map { SerializedBrick.fromBrick(it) },
                score = state.score,
                spirit = SerializedSpirit.fromSpirit(state.spirit),
                spiritReserve = state.spiritReserve.map { SerializedSpirit.fromSpirit(it) },
                gameStatus = state.gameStatus,
                line = state.line,
                heldSpirit = SerializedSpirit.fromSpirit(state.heldSpirit),
                hasHeld = state.hasHeld,
                combo = state.combo,
                backToBack = state.backToBack,
                tSpinCount = state.tSpinCount,
                clearedIndices = state.clearedIndices
            )
        }
    }

    @Serializable
    private data class SerializedBrick(val x: Float, val y: Float, val colorIndex: Int = 0) {
        fun toBrick(): Brick = Brick(Point(x, y), colorIndex)
        companion object {
            fun fromBrick(brick: Brick): SerializedBrick =
                SerializedBrick(brick.location.x, brick.location.y, brick.colorIndex)
        }
    }

    @Serializable
    private data class SerializedSpirit(
        val shape: List<SerializedPoint> = emptyList(),
        val offset: SerializedPoint = SerializedPoint(0f, 0f),
        val colorIndex: Int = 0,
        val rotationState: Int = 0
    ) {
        fun toSpirit(): Spirit = Spirit(
            shape = shape.map { it.toPoint() },
            offset = offset.toPoint(),
            colorIndex = colorIndex,
            rotationState = rotationState
        )

        companion object {
            fun fromSpirit(spirit: Spirit): SerializedSpirit = SerializedSpirit(
                shape = spirit.shape.map { SerializedPoint.fromPoint(it) },
                offset = SerializedPoint.fromPoint(spirit.offset),
                colorIndex = spirit.colorIndex,
                rotationState = spirit.rotationState
            )
        }
    }

    @Serializable
    private data class SerializedPoint(val x: Float, val y: Float) {
        fun toPoint(): Point = Point(x, y)
        companion object {
            fun fromPoint(point: Point): SerializedPoint = SerializedPoint(point.x, point.y)
        }
    }
}
