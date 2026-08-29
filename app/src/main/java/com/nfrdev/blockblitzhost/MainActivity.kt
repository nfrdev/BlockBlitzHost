package com.nfrdev.blockblitzhost

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nfrdev.blockblitzhost.blockblitz.*
import com.nfrdev.blockblitzhost.notifications.DailyChallengeWorker
import com.nfrdev.blockblitzhost.notifications.NotificationHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

internal val android.content.Context.blockBlitzDataStore: DataStore<Preferences> by preferencesDataStore(name = "blockblitz_prefs")

private val HighScorePrefKey = intPreferencesKey("blockblitz_high_score")
private val GamesPlayedPrefKey = intPreferencesKey("blockblitz_games_played")
private val LinesClearedPrefKey = intPreferencesKey("blockblitz_lines_cleared")
private val SoundMutedPrefKey = booleanPreferencesKey("blockblitz_sound_muted")
private val LeaderboardPrefKey = stringPreferencesKey("blockblitz_leaderboard")
private val ThemePrefKey = stringPreferencesKey("blockblitz_theme")
private val HapticEnabledPrefKey = booleanPreferencesKey("blockblitz_haptic_enabled")
private val NotificationsEnabledPrefKey = booleanPreferencesKey("blockblitz_notifications_enabled")
private val SkippedVersionPrefKey = intPreferencesKey("blockblitz_skipped_version")

private data class PlayerRank(val tier: String, val title: String, val vectorIcon: androidx.compose.ui.graphics.vector.ImageVector, val nextGoal: Int)

private fun getPlayerRank(score: Int): PlayerRank {
    return when {
        score >= 30000 -> PlayerRank("V", "GRANDMASTER BLITZ", Icons.Rounded.WorkspacePremium, 50000)
        score >= 15000 -> PlayerRank("IV", "DIAMOND MASTER", Icons.Rounded.Diamond, 30000)
        score >= 5000 -> PlayerRank("III", "GOLD CHAMPION", Icons.Rounded.MilitaryTech, 15000)
        score >= 1000 -> PlayerRank("II", "SILVER STRIKER", Icons.Rounded.MilitaryTech, 5000)
        else -> PlayerRank("I", "BRONZE ROOKIE", Icons.Rounded.MilitaryTech, 1000)
    }
}

enum class AppState { Splash, Welcome, Game }

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleDailyChallenge()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createChannels(this)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            scheduleDailyChallenge()
        }

        setContent {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                SoundUtil.init(this@MainActivity)
            }
            MaterialTheme {
                var appState by rememberSaveable { mutableStateOf(AppState.Splash) }
                var selectedGameMode by rememberSaveable { mutableStateOf(GameMode.Marathon) }
                
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                val updateManager = remember { UpdateManager(this@MainActivity) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    delay(2500)
                    appState = AppState.Welcome
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val skipped = blockBlitzDataStore.data.first()[SkippedVersionPrefKey] ?: 0
                    val info = updateManager.checkUpdate()
                    if (info != null && (info.forceUpdate || info.versionCode != skipped)) {
                        updateInfo = info
                    }
                }

                updateInfo?.let { info ->
                    UpdateDialog(
                        updateInfo = info,
                        onDismiss = {
                            if (!info.forceUpdate) {
                                lifecycleScope.launch {
                                    blockBlitzDataStore.edit { it[SkippedVersionPrefKey] = info.versionCode }
                                }
                                updateInfo = null
                            }
                        },
                        onUpdate = { updateInfo = null },
                        updateManager = updateManager
                    )
                }

                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = {
                            when {
                                initialState == AppState.Splash && targetState == AppState.Welcome -> {
                                    (fadeIn(tween(1000)) + scaleIn(initialScale = 0.92f, animationSpec = tween(1000, easing = EaseOutQuart)))
                                        .togetherWith(fadeOut(tween(800)))
                                }
                                targetState == AppState.Game -> {
                                    (fadeIn(tween(600)) + scaleIn(initialScale = 0.85f, animationSpec = tween(600, easing = EaseOutBack)))
                                        .togetherWith(fadeOut(tween(400)))
                                }
                                else -> {
                                    (fadeIn(tween(500)) + scaleIn(initialScale = 1.05f, animationSpec = tween(500)))
                                        .togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 0.95f, animationSpec = tween(400)))
                                }
                            }
                        },
                        label = "MainAppTransition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            AppState.Splash -> {
                                SplashScreen(
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedContentScope = this@AnimatedContent
                                )
                            }
                            AppState.Welcome -> {
                                BlockBlitzWelcomeScreen(
                                    selectedMode = selectedGameMode,
                                    onModeChange = { selectedGameMode = it },
                                    onPlay = { appState = AppState.Game },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedContentScope = this@AnimatedContent,
                                    onRequestNotificationPermission = { checkNotificationPermission() },
                                    onUpdateFound = { info -> updateInfo = info }
                                )
                            }
                            AppState.Game -> {
                                BlockBlitzScreen(
                                    gameMode = selectedGameMode,
                                    onExit = { appState = AppState.Welcome },
                                    onRequestNotificationPermission = { checkNotificationPermission() },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedContentScope = this@AnimatedContent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SoundUtil.release()
        super.onDestroy()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleDailyChallenge()
            }
        } else {
            scheduleDailyChallenge()
        }
    }

    private fun scheduleDailyChallenge() {
        lifecycleScope.launch {
            val enabled = blockBlitzDataStore.data.first()[NotificationsEnabledPrefKey] ?: false
            if (!enabled) return@launch

            val dailyRequest = PeriodicWorkRequestBuilder<DailyChallengeWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "daily_challenge_work",
                ExistingPeriodicWorkPolicy.KEEP,
                dailyRequest
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SplashScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A14)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DecorativeBlock(
                    color = Color(0xFFA855F7),
                    points = listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2),
                    modifier = Modifier.size(80.dp),
                    uniformAlpha = 0.6f
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "BLOCK BLITZ",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF22D3EE),
                                Color(0xFFA855F7),
                                Color(0xFFF472B6)
                            )
                        )
                    ),
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "title"),
                        animatedVisibilityScope = animatedContentScope
                    )
                )
                Text(
                    text = "NFR DEV",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BlockBlitzWelcomeScreen(
    selectedMode: GameMode = GameMode.Marathon,
    onModeChange: (GameMode) -> Unit = {},
    onPlay: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onRequestNotificationPermission: () -> Unit = {},
    onUpdateFound: (UpdateInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        contentVisible = true
    }

    val highScoreFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[HighScorePrefKey] ?: 0
        }
    }
    val highScore by highScoreFlow.collectAsState(initial = 0)

    val gamesPlayedFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[GamesPlayedPrefKey] ?: 0
        }
    }
    val gamesPlayed by gamesPlayedFlow.collectAsState(initial = 0)

    val linesClearedFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[LinesClearedPrefKey] ?: 0
        }
    }
    val linesCleared by linesClearedFlow.collectAsState(initial = 0)

    val soundMutedFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[SoundMutedPrefKey] ?: false
        }
    }
    val isSoundMuted by soundMutedFlow.collectAsState(initial = false)

    val leaderboardFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[LeaderboardPrefKey]?.let { raw ->
                runCatching { Json.decodeFromString<List<LeaderboardEntry>>(raw) }.getOrNull()
            } ?: emptyList()
        }
    }
    val leaderboard by leaderboardFlow.collectAsState(initial = emptyList())

    val themeFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[ThemePrefKey]?.let { raw ->
                runCatching { GameTheme.valueOf(raw) }.getOrNull()
            } ?: GameTheme.Cyberpunk
        }
    }
    val currentTheme by themeFlow.collectAsState(initial = GameTheme.Cyberpunk)

    val hapticEnabledFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[HapticEnabledPrefKey] ?: true
        }
    }
    val isHapticEnabled by hapticEnabledFlow.collectAsState(initial = true)

    val notificationsEnabledFlow = remember {
        context.blockBlitzDataStore.data.map { preferences ->
            preferences[NotificationsEnabledPrefKey] ?: false
        }
    }
    val isNotificationsEnabled by notificationsEnabledFlow.collectAsState(initial = true)

    var showHowToPlay by rememberSaveable { mutableStateOf(false) }
    var showStatsDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "MainMenuEffects")
    val buttonGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ButtonGlowPulse"
    )
    val buttonScalePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ButtonScalePulse"
    )

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B0F19),
                            Color(0xFF131B2E),
                            Color(0xFF0F172A),
                            Color(0xFF070A14)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedFallingBlocksBackground()

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                                .border(1.5.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { showSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                                .border(1.5.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { showHowToPlay = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = "How to Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .border(1.5.dp, Color(0x38F59E0B), CircleShape)
                            .clickable { showStatsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val rank = getPlayerRank(highScore)
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "Profile",
                            tint = when (rank.tier) {
                                "V" -> Color(0xFFFBBF24)
                                "IV" -> Color(0xFF22D3EE)
                                "III" -> Color(0xFFFFD700)
                                "II" -> Color(0xFFC0C0C0)
                                else -> Color(0xFFCD7F32)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp, bottom = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6).copy(alpha = 0.20f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DecorativeBlock(
                        color = Color(0xFFA855F7),
                        points = listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2),
                        modifier = Modifier.size(36.dp),
                        uniformAlpha = 0.45f
                    )
                }

                Text(
                    text = "BLOCK BLITZ",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF22D3EE),
                                Color(0xFFA855F7),
                                Color(0xFFF472B6)
                            )
                        )
                    ),
                    modifier = Modifier
                        .sharedElement(
                            rememberSharedContentState(key = "title"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .padding(top = 10.dp, bottom = 4.dp)
                )

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(800, delayMillis = 200)) + slideInVertically(initialOffsetY = { 20 })
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ARCADE EDITION",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 5.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Text(
                            text = "SELECT GAME MODE",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                        ) {
                            GameMode.entries.forEach { mode ->
                                val isSelected = selectedMode == mode
                                val modeGlow by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 0.7f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "ModeGlow_${mode.name}"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF8B5CF6).copy(alpha = modeGlow),
                                                        Color(0xFFEC4899).copy(alpha = modeGlow)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF)))
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFA855F7) else Color(0x33FFFFFF),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable { onModeChange(mode) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = mode.icon,
                                            contentDescription = mode.title,
                                            tint = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    if (isSelected) {
                                                        scaleX = 1.15f
                                                        scaleY = 1.15f
                                                    }
                                                }
                                        )
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = selectedMode.description,
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(240.dp, 58.dp)
                                .graphicsLayer {
                                    val baseScale = if (isPlayPressed) 0.94f else buttonScalePulse
                                    scaleX = baseScale
                                    scaleY = baseScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFEC4899).copy(alpha = 0.55f * buttonGlowPulse),
                                                Color(0xFF8B5CF6).copy(alpha = 0.35f * buttonGlowPulse),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                            )

                            Button(
                                onClick = onPlay,
                                interactionSource = playInteractionSource,
                                shape = RoundedCornerShape(30.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                        ),
                                        shape = RoundedCornerShape(30.dp)
                                    )
                                    .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(30.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "START PLAYING",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "▶", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                Text(text = "BEST", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text(text = "$highScore", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.size(1.dp, 12.dp).background(Color(0x33FFFFFF)))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(14.dp))
                                Text(text = "PLAYED", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text(text = "$gamesPlayed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SocialChip(label = "Telegram", url = "https://t.me/nfrdevhub", iconRes = R.drawable.ic_telegram)
                                SocialChip(label = "GitHub", url = "https://github.com/nfrdev", iconRes = R.drawable.ic_github, iconTint = Color.White)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showHowToPlay,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { showHowToPlay = false },
                    contentAlignment = Alignment.Center
                ) {
                    HowToPlayContent(onDismiss = { showHowToPlay = false })
                }
            }

            if (showStatsDialog) {
                PlayerStatsDialog(
                    highScore = highScore,
                    gamesPlayed = gamesPlayed,
                    linesCleared = linesCleared,
                    leaderboard = leaderboard,
                    onDismiss = { showStatsDialog = false }
                )
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    currentTheme = currentTheme,
                    isSoundMuted = isSoundMuted,
                    isHapticEnabled = isHapticEnabled,
                    isNotificationsEnabled = isNotificationsEnabled,
                    updateManager = UpdateManager(context),
                    onThemeSelect = { theme ->
                        coroutineScope.launch { context.blockBlitzDataStore.edit { it[ThemePrefKey] = theme.name } }
                    },
                    onToggleSound = {
                        coroutineScope.launch { context.blockBlitzDataStore.edit { it[SoundMutedPrefKey] = !isSoundMuted } }
                    },
                    onToggleHaptic = {
                        coroutineScope.launch { context.blockBlitzDataStore.edit { it[HapticEnabledPrefKey] = !isHapticEnabled } }
                    },
                    onToggleNotifications = {
                        coroutineScope.launch { 
                            val newValue = !isNotificationsEnabled
                            context.blockBlitzDataStore.edit { it[NotificationsEnabledPrefKey] = newValue }
                            if (newValue) {
                                onRequestNotificationPermission()
                            } else {
                                WorkManager.getInstance(context).cancelUniqueWork("daily_challenge_work")
                            }
                        }
                    },
                    onUpdateFound = { info ->
                        showSettingsDialog = false
                        onUpdateFound(info)
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
    }
}

@Composable
private fun HowToPlayContent(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))))
            .border(1.5.dp, Color(0x448B5CF6), RoundedCornerShape(24.dp))
            .clickable(enabled = false) { }
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(text = "HOW TO PLAY", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Text(text = "MASTER THE CONTROLS & SCORING", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ControlRow(icon = Icons.Rounded.SyncAlt, title = "Move Left / Right", desc = "Tap buttons or slide piece")
                ControlRow(icon = Icons.AutoMirrored.Rounded.RotateRight, title = "Rotate 90°", desc = "Tap Rotate or tap active block")
                ControlRow(icon = Icons.Rounded.KeyboardArrowDown, title = "Soft Drop", desc = "Drag down or hold down button")
                ControlRow(icon = Icons.Rounded.ElectricBolt, title = "Hard Drop", desc = "Tap Hard Drop for instant slam")
                ControlRow(icon = Icons.Rounded.Inventory2, title = "Hold Slot", desc = "Stash current brick for later")
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x2210B981)).border(1.dp, Color(0x4410B981), RoundedCornerShape(12.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFA7F3D0), modifier = Modifier.size(16.dp))
                    Text(text = "Pro Tip: Clear 4 lines at once for a Tetris Blitz & Combo Multiplier!", color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                modifier = Modifier.fillMaxWidth().height(46.dp).background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))), shape = RoundedCornerShape(16.dp))
            ) {
                Text(text = "GOT IT, LET'S PLAY!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun ControlRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x1AFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
        }
        Column {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlayerStatsDialog(highScore: Int, gamesPlayed: Int, linesCleared: Int, leaderboard: List<LeaderboardEntry>, onDismiss: () -> Unit) {
    val rank = getPlayerRank(highScore)
    val prevGoal = when (rank.tier) {
        "V" -> 30000
        "IV" -> 15000
        "III" -> 5000
        "II" -> 1000
        else -> 0
    }
    val progress = ((highScore - prevGoal).toFloat() / (rank.nextGoal - prevGoal).toFloat()).coerceIn(0.05f, 1f)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))).border(1.5.dp, Color(0x44F59E0B), RoundedCornerShape(24.dp))) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x1AFFFFFF)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text(text = "✕", color = Color.White, fontSize = 14.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text(text = "PLAYER PROFILE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text(text = "CAREER STATS & RANK", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Color(0x33F59E0B), Color(0x33EC4899)))).border(1.dp, Color(0x55F59E0B), RoundedCornerShape(16.dp)).padding(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0x1AFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = rank.vectorIcon,
                                contentDescription = null,
                                tint = when (rank.tier) {
                                    "V" -> Color(0xFFFBBF24)
                                    "IV" -> Color(0xFF22D3EE)
                                    "III" -> Color(0xFFFFD700)
                                    "II" -> Color(0xFFC0C0C0)
                                    else -> Color(0xFFCD7F32)
                                },
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(text = rank.title, color = Color(0xFFFBBF24), fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(text = "TIER ${rank.tier} • NEXT GOAL: ${rank.nextGoal} PTS", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                            Box(modifier = Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899)))))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.EmojiEvents, label = "BEST SCORE", value = highScore.toString(), accentColor = Color(0xFFF59E0B))
                    StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.SportsEsports, label = "GAMES PLAYED", value = gamesPlayed.toString(), accentColor = Color(0xFF22D3EE))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.Widgets, label = "LINES CLEARED", value = linesCleared.toString(), accentColor = Color(0xFF10B981))
                    StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.ElectricBolt, label = "GLOBAL RANK", value = "Tier ${rank.tier}", accentColor = Color(0xFFA855F7))
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Text(text = "TOP RUNS", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (leaderboard.isEmpty()) {
                        Text(text = "No runs recorded yet. Complete a game to record your score!", color = Color(0xFF94A3B8), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    } else {
                        leaderboard.take(5).forEachIndexed { index, entry ->
                            val medalColor = when (index) { 
                                0 -> Color(0xFFFBBF24) // Gold
                                1 -> Color(0xFFE2E8F0) // Silver
                                2 -> Color(0xFFCD7F32) // Bronze
                                else -> Color(0xFF64748B) 
                            }
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x18FFFFFF)).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (index < 3) Icons.Rounded.EmojiEvents else Icons.Rounded.Tag,
                                        contentDescription = null,
                                        tint = medalColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(text = "${entry.score} pts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "• ${entry.mode}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                                Text(text = entry.dateFormatted, color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Rounded.WorkspacePremium, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Text(text = "ACHIEVEMENTS", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    val achievements = AchievementList.ALL
                    achievements.forEach { achievement ->
                        val isUnlocked = leaderboard.any { it.achievements.contains(achievement.id) }
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isUnlocked) Color(0x22F59E0B) else Color(0x14FFFFFF)).border(1.dp, if (isUnlocked) Color(0x55F59E0B) else Color(0x22FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isUnlocked) Color(0x33F59E0B) else Color(0x1AFFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = achievement.vectorIcon,
                                        contentDescription = null,
                                        tint = if (isUnlocked) Color(0xFFF59E0B) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(text = achievement.name, color = if (isUnlocked) Color.White else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = achievement.description, color = Color(0xFF64748B), fontSize = 9.sp)
                                }
                            }
                            Text(text = if (isUnlocked) "✓" else "🔒", color = if (isUnlocked) Color(0xFF10B981) else Color(0xFF64748B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)), border = BorderStroke(1.dp, Color(0x44F59E0B)), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(text = "CLOSE PROFILE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    currentTheme: GameTheme,
    isSoundMuted: Boolean,
    isHapticEnabled: Boolean,
    isNotificationsEnabled: Boolean,
    updateManager: UpdateManager,
    onThemeSelect: (GameTheme) -> Unit,
    onToggleSound: () -> Unit,
    onToggleHaptic: () -> Unit,
    onToggleNotifications: () -> Unit,
    onUpdateFound: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))).border(1.5.dp, Color(0x448B5CF6), RoundedCornerShape(24.dp))) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x1AFFFFFF)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text(text = "✕", color = Color.White, fontSize = 14.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "⚙️ SETTINGS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(text = "PREFERENCES & VISUALS", color = Color(0xFF8B5CF6), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggle(
                        icon = if (isSoundMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, 
                        title = "Sound Effects", 
                        isEnabled = !isSoundMuted, 
                        onToggle = onToggleSound
                    )
                    SettingsToggle(
                        icon = Icons.Rounded.Vibration, 
                        title = "Haptic Feedback", 
                        isEnabled = isHapticEnabled, 
                        onToggle = onToggleHaptic
                    )
                    SettingsToggle(
                        icon = if (isNotificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        title = "Notifications",
                        isEnabled = isNotificationsEnabled,
                        onToggle = onToggleNotifications
                    )
                }

                // Check for Updates
                val coroutineScope = rememberCoroutineScope()
                var updateCheckState by remember { mutableStateOf<String?>(null) } // null=idle, "checking", "up_to_date", "found"
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = updateCheckState != "checking") {
                                coroutineScope.launch {
                                    updateCheckState = "checking"
                                    val info = updateManager.checkUpdate()
                                    if (info != null) {
                                        updateCheckState = "found"
                                        kotlinx.coroutines.delay(400)
                                        onUpdateFound(info)
                                    } else {
                                        updateCheckState = "up_to_date"
                                    }
                                }
                            }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = when (updateCheckState) {
                                    "found" -> Color(0xFF10B981)
                                    "up_to_date" -> Color(0xFF22D3EE)
                                    else -> Color(0xFF22D3EE)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(text = "Check for Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        when (updateCheckState) {
                            "checking" -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF8B5CF6), strokeWidth = 2.dp)
                            "up_to_date" -> Text("Up to date ✓", color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            "found" -> Text("Update found!", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            else -> Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "🎨 VISUAL THEME", color = Color(0xFF8B5CF6), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameTheme.entries.forEach { theme ->
                            val isSelected = theme == currentTheme
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) theme.bgGlow.copy(alpha = 0.35f) else Color(0x14FFFFFF)).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) theme.bgGlow else Color(0x22FFFFFF), shape = RoundedCornerShape(12.dp)).clickable { onThemeSelect(theme) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = theme.icon, contentDescription = theme.title, tint = if (isSelected) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
                                    Text(text = theme.name, color = if (isSelected) Color.White else Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(), modifier = Modifier.fillMaxWidth().height(48.dp).background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))), shape = RoundedCornerShape(16.dp))) {
                    Text(text = "SAVE & CLOSE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, isEnabled: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isEnabled) Color(0xFF22D3EE) else Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.size(44.dp, 24.dp).clip(CircleShape).background(if (isEnabled) Color(0xFF10B981) else Color(0x33FFFFFF)).padding(horizontal = 4.dp), contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, accentColor: Color) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x16FFFFFF)).border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AnimatedFallingBlocksBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundFallingPieces")
    val progress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(16000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "FallingProgress")
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val gridStep = 40.dp.toPx()
        if (gridStep > 0) {
            var xGrid = 0f
            while (xGrid < width) { drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(xGrid, 0f), end = Offset(xGrid, height), strokeWidth = 1f); xGrid += gridStep }
            var yGrid = 0f
            while (yGrid < height) { drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(0f, yGrid), end = Offset(width, yGrid), strokeWidth = 1f); yGrid += gridStep }
        }
        val fallingBlocks = listOf(
            FallingBlockSpec(0.12f, 1.0f, 0.0f, Color(0xFF06B6D4), listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3)),
            FallingBlockSpec(0.85f, 1.2f, 0.3f, Color(0xFF8B5CF6), listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
            FallingBlockSpec(0.32f, 0.8f, 0.6f, Color(0xFFEC4899), listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2)),
            FallingBlockSpec(0.70f, 1.1f, 0.15f, Color(0xFF10B981), listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
            FallingBlockSpec(0.48f, 0.9f, 0.45f, Color(0xFFF59E0B), listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),
            FallingBlockSpec(0.22f, 1.3f, 0.75f, Color(0xFF3B82F6), listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2)),
            FallingBlockSpec(0.92f, 0.85f, 0.5f, Color(0xFFF43F5E), listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1))
        )
        val blockSize = 14.dp.toPx()
        fallingBlocks.forEach { spec ->
            val startY = -120.dp.toPx()
            val totalDistance = height + 240.dp.toPx()
            val currentY = startY + (((progress * spec.speedMult + spec.yOffsetOffset) % 1f) * totalDistance)
            val currentX = width * spec.xPercent
            spec.points.forEach { (px, py) ->
                val blockX = currentX + px * blockSize
                val blockY = currentY + py * blockSize
                drawRoundRect(color = spec.color.copy(alpha = 0.22f), topLeft = Offset(blockX, blockY), size = Size(blockSize - 2f, blockSize - 2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
                drawRoundRect(color = spec.color.copy(alpha = 0.40f), topLeft = Offset(blockX, blockY), size = Size(blockSize - 2f, blockSize - 2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f), style = Stroke(width = 1.5f))
                drawRoundRect(color = Color.White.copy(alpha = 0.15f), topLeft = Offset(blockX + 2f, blockY + 2f), size = Size(blockSize / 3f, blockSize / 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f))
            }
        }
    }
}

private data class FallingBlockSpec(val xPercent: Float, val speedMult: Float, val yOffsetOffset: Float, val color: Color, val points: List<Pair<Int, Int>>)

@Composable
fun DecorativeBlock(color: Color, points: List<Pair<Int, Int>>, modifier: Modifier = Modifier, uniformAlpha: Float = 0.35f) {
    val infiniteTransition = rememberInfiniteTransition(label = "DecorativeBlockTransition")
    val floatAnim by infiniteTransition.animateFloat(initialValue = -5f, targetValue = 5f, animationSpec = infiniteRepeatable(animation = tween(2500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "Floating")
    val scaleAnim by infiniteTransition.animateFloat(initialValue = 0.95f, targetValue = 1.05f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "Pulse")
    val rotationAnim by infiniteTransition.animateFloat(initialValue = -3f, targetValue = 3f, animationSpec = infiniteRepeatable(animation = tween(3500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "Rotation")
    val glowAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1.0f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "GlowAlpha")
    Canvas(modifier = modifier.size(64.dp).graphicsLayer { translationY = floatAnim; scaleX = scaleAnim; scaleY = scaleAnim; rotationZ = rotationAnim }) {
        val cellSize = size.width / 4f
        points.forEach { (px, py) ->
            val topLeft = Offset(px * cellSize + 1f, py * cellSize + 1f)
            val rectSize = Size(cellSize - 2f, cellSize - 2f)
            drawRoundRect(color = color.copy(alpha = uniformAlpha), topLeft = topLeft, size = rectSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            drawRoundRect(color = color.copy(alpha = (uniformAlpha * 1.6f * glowAlpha).coerceAtMost(0.9f)), topLeft = topLeft, size = rectSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f), style = Stroke(width = 2f))
            drawRoundRect(color = Color.White.copy(alpha = uniformAlpha * 0.7f * glowAlpha), topLeft = Offset(px * cellSize + 3f, py * cellSize + 3f), size = Size(cellSize / 3f, cellSize / 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f))
        }
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    updateManager: UpdateManager
) {
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var verificationFailed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!updateInfo.forceUpdate && !isDownloading) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !updateInfo.forceUpdate, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))))
                .border(1.5.dp, Color(0x448B5CF6), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Icon + title
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (updateInfo.forceUpdate) "Required Update" else "Update Available",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                // Version badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x228B5CF6))
                        .border(1.dp, Color(0x558B5CF6), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = "v${updateInfo.versionName}", color = Color(0xFFA855F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Update message
                Text(
                    text = updateInfo.updateMessage,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                if (updateInfo.forceUpdate) {
                    Text(
                        text = "⚠️ This update is required to continue playing.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                if (verificationFailed) {
                    Text(
                        text = "❌ Download verification failed. Please try again.",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Progress bar
                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFA855F7),
                            trackColor = Color(0x33FFFFFF)
                        )
                        Text(
                            text = "Downloading... $downloadProgress%",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Buttons
                if (!isDownloading) {
                    Button(
                        onClick = {
                            isDownloading = true
                            verificationFailed = false
                            updateManager.downloadAndInstall(
                                apkUrl = updateInfo.apkUrl,
                                versionName = updateInfo.versionName,
                                expectedSha256 = updateInfo.sha256,
                                onProgress = { downloadProgress = it },
                                onSuccess = { onUpdate() },
                                onError = { isDownloading = false; verificationFailed = true }
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text("Update Now", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }

                    if (!updateInfo.forceUpdate) {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Skip this version", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialChip(label: String, url: String, iconRes: Int, iconTint: Color = Color.Unspecified) {
    val uriHandler = LocalUriHandler.current
    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)).clickable { try { uriHandler.openUri(url) } catch (_: Exception) { } }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(text = label, color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}
