package com.nfrdev.blockblitzhost.blockblitz


enum class Direction {
    Left, Up, Right, Down
}

fun Direction.toOffset() = when (this) {
    Direction.Left -> -1 to 0
    Direction.Up -> 0 to -1
    Direction.Right -> 1 to 0
    Direction.Down -> 0 to 1
}

val NextMatrix = 4 to 2
const val ScoreEverySpirit = 12

fun calculateScore(lines: Int) = when (lines) {
    1 -> 100
    2 -> 300
    3 -> 700
    4 -> 1500
    else -> 0
}

sealed class SoundType {
    data object Move : SoundType()
    data object Rotate : SoundType()
    data object Start : SoundType()
    data object Drop : SoundType()
    data object Clean : SoundType()
}

object SoundUtil {
    fun init(any: Any? = null) = Unit
    fun release() = Unit
    fun play(isMute: Boolean, sound: SoundType) = Unit
}

val Sounds = listOf(
    SoundType.Move,
    SoundType.Rotate,
    SoundType.Start,
    SoundType.Drop,
    SoundType.Clean
)
