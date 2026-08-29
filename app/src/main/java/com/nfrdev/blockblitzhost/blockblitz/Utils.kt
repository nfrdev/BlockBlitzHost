package com.nfrdev.blockblitzhost.blockblitz

import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class Direction {
    Left, Up, Right, Down
}

enum class GameMode(val title: String, val icon: ImageVector, val description: String, val accentColor: Color) {
    Marathon("Marathon", Icons.Rounded.ElectricBolt, "Classic endless progression with increasing speed", Color(0xFF8B5CF6)),
    Blitz("2-Min Blitz", Icons.Rounded.Timer, "Fast-paced arcade sprint for highest score", Color(0xFF06B6D4)),
    Zen("Zen Mode", Icons.Rounded.SelfImprovement, "Relaxed & steady tempo for calm practice", Color(0xFF10B981)),
    DailyChallenge("Daily", Icons.Rounded.AdsClick, "Complete today's fixed piece sequence—compare your score!", Color(0xFFF59E0B))
}

enum class GameTheme(val title: String, val icon: ImageVector, val bgGlow: Color) {
    Cyberpunk("Cyberpunk Neon", Icons.Rounded.AutoAwesome, Color(0xFF8B5CF6)),
    GameBoy("GameBoy 1989", Icons.Rounded.VideogameAsset, Color(0xFF306230)),
    Pastel("Pastel Synth", Icons.Rounded.Palette, Color(0xFFF472B6)),
    Amoled("AMOLED Pure", Icons.Rounded.DarkMode, Color(0xFF00F0FF))
}

fun getThemeColors(theme: GameTheme): List<Color> {
    return when (theme) {
        GameTheme.Cyberpunk -> listOf(
            Color(0xFF06B6D4), // 0: Cyan (I)
            Color(0xFF3B82F6), // 1: Blue (J)
            Color(0xFFF97316), // 2: Orange (L)
            Color(0xFFF59E0B), // 3: Yellow (O)
            Color(0xFF10B981), // 4: Green (S)
            Color(0xFF8B5CF6), // 5: Purple (T)
            Color(0xFFEF4444)  // 6: Red (Z)
        )
        GameTheme.GameBoy -> listOf(
            Color(0xFF9BBC0F), // Light Phosphor
            Color(0xFF8BAC0F), // Medium Light
            Color(0xFF306230), // Dark Green
            Color(0xFF9BBC0F),
            Color(0xFF8BAC0F),
            Color(0xFF306230),
            Color(0xFF0F380F)  // Darkest Olive
        )
        GameTheme.Pastel -> listOf(
            Color(0xFF67E8F9), // Minty Sky
            Color(0xFF93C5FD), // Baby Blue
            Color(0xFFFDBA74), // Pastel Peach
            Color(0xFFFDE047), // Soft Butter
            Color(0xFF6EE7B7), // Seafoam
            Color(0xFFC4B5FD), // Lavender
            Color(0xFFFCA5A5)  // Soft Rose
        )
        GameTheme.Amoled -> listOf(
            Color(0xFF00F0FF), // Ultra Cyan
            Color(0xFF0051FF), // Deep Electric Blue
            Color(0xFFFF6A00), // Pure Neon Orange
            Color(0xFFFFD600), // Pure Neon Yellow
            Color(0xFF00FF66), // Acid Green
            Color(0xFFB800FF), // Electric Violet
            Color(0xFFFF003C)  // Laser Crimson
        )
    }
}

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val icon: String,
    val description: String
) {
    val vectorIcon: ImageVector
        get() = when (id) {
            "first_1000" -> Icons.Rounded.MilitaryTech
            "tspin_master" -> Icons.AutoMirrored.Rounded.RotateRight
            "blitz_hero" -> Icons.Rounded.ElectricBolt
            "line_breaker" -> Icons.Rounded.Layers
            "combo_king" -> Icons.Rounded.AutoAwesome
            "zen_master" -> Icons.Rounded.SelfImprovement
            "no_pause" -> Icons.Rounded.TimerOff
            "first_game" -> Icons.Rounded.SportsEsports
            else -> Icons.Rounded.EmojiEvents
        }
}

@Serializable
data class LeaderboardEntry(
    val score: Int,
    val mode: String,
    val lines: Int,
    val dateFormatted: String,
    val achievements: Set<String> = emptySet()
)

fun Direction.toOffset() = when (this) {
    Direction.Left -> -1 to 0
    Direction.Up -> 0 to -1
    Direction.Right -> 1 to 0
    Direction.Down -> 0 to 1
}

val NextMatrix = 4 to 2
const val ScoreEverySpirit = 12

fun calculateScore(
    lines: Int,
    isTSpin: Boolean = false,
    isMiniTSpin: Boolean = false,
    backToBack: Boolean = false
): Int {
    val base = if (isTSpin) {
        when (lines) {
            0 -> 400
            1 -> 800
            2 -> 1200
            3 -> 1600
            else -> 0
        }
    } else if (isMiniTSpin) {
        when (lines) {
            0 -> 100
            1 -> 200
            2 -> 400
            else -> 0
        }
    } else {
        when (lines) {
            1 -> 100
            2 -> 300
            3 -> 700
            4 -> 1500
            else -> 0
        }
    }

    val isDifficult = lines == 4 || ((isTSpin || isMiniTSpin) && lines > 0)
    val multiplier = if (backToBack && isDifficult) 1.5f else 1.0f
    return (base * multiplier).toInt()
}

sealed class SoundType {
    data object Move : SoundType()
    data object Rotate : SoundType()
    data object Start : SoundType()
    data object Drop : SoundType()
    data object Clean : SoundType()
}

object SoundUtil {
    private var soundPool: android.media.SoundPool? = null
    private val soundMap = mutableMapOf<SoundType, Int>()

    fun init(context: android.content.Context) {
        if (soundPool != null) return
        val appContext = context.applicationContext
        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_GAME)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attributes)
            .build()
        soundPool?.let { pool ->
            soundMap[SoundType.Move] = pool.load(appContext, com.nfrdev.blockblitzhost.R.raw.move, 1)
            soundMap[SoundType.Rotate] = pool.load(appContext, com.nfrdev.blockblitzhost.R.raw.rotate, 1)
            soundMap[SoundType.Start] = pool.load(appContext, com.nfrdev.blockblitzhost.R.raw.start, 1)
            soundMap[SoundType.Drop] = pool.load(appContext, com.nfrdev.blockblitzhost.R.raw.drop, 1)
            soundMap[SoundType.Clean] = pool.load(appContext, com.nfrdev.blockblitzhost.R.raw.clean, 1)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundMap.clear()
    }

    fun play(isMute: Boolean, sound: SoundType) {
        if (isMute) return
        val soundId = soundMap[sound] ?: return
        soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}

val Sounds = listOf(
    SoundType.Move,
    SoundType.Rotate,
    SoundType.Start,
    SoundType.Drop,
    SoundType.Clean
)

object HapticPatterns {
    fun lineClearPattern(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    suspend fun tSpinPattern(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        repeat(3) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(35)
        }
    }
    suspend fun comboPattern(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, combo: Int) {
        val pulses = minOf(5, combo / 5)
        repeat(pulses) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            kotlinx.coroutines.delay(60)
        }
    }
    fun gameOverPattern(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
}

object AchievementList {
    val ALL = listOf(
        Achievement("first_1000", "First 1000", "🥇", "Score 1,000 points"),
        Achievement("tspin_master", "T-Spin Master", "🔴", "Land 5+ T-spins in one game"),
        Achievement("blitz_hero", "Blitz Champion", "⚡", "Score 5,000+ in Blitz mode"),
        Achievement("line_breaker", "Line Breaker", "🧱", "Clear 40+ lines in Marathon"),
        Achievement("combo_king", "Combo King", "🎯", "Achieve 10+ combo"),
        Achievement("zen_master", "Zen Master", "🧘", "Score 2,000+ in Zen mode"),
        Achievement("no_pause", "Perfect Focus", "🤐", "Complete a game without pausing"),
        Achievement("first_game", "Starting Out", "🎮", "Complete your first game")
    )
    fun getAchievement(id: String): Achievement? = ALL.find { it.id == id }
}
