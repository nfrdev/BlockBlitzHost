package com.nfrdev.blockblitzhost.blockblitz

data class Spirit(
    val shape: List<Point> = emptyList(),
    val offset: Point = Point.of(0, 0),
    val colorIndex: Int = 0,
    val pieceType: PieceType = PieceType.T,
    val rotationState: Int = 0 // 0: Spawn, 1: 90° CW, 2: 180°, 3: 270° CW
) {
    val location: List<Point> = shape.map { it + offset }

    fun moveBy(step: Pair<Int, Int>): Spirit =
        copy(offset = offset + Point.of(step.first, step.second))

    /**
     * Rotates piece shape clockwise by 90 degrees around origin.
     * O-piece is invariant under rotation.
     */
    fun rotateShape(): Spirit {
        if (pieceType == PieceType.O) return this
        val newShape = shape.map { Point(-it.y, it.x) }
        return copy(shape = newShape, rotationState = (rotationState + 1) % 4)
    }

    companion object {
        val Empty = Spirit()
    }
}

fun Spirit.toSpawnState(matrix: Pair<Int, Int>): Spirit {
    val spawnX = matrix.first / 2 - 1
    return Spirit(
        shape = TetrominoShapes.getValue(pieceType),
        offset = Point.of(spawnX, 1),
        colorIndex = PieceColorIndices.getValue(pieceType),
        pieceType = pieceType,
        rotationState = 0
    )
}

enum class PieceType { Z, S, I, T, O, L, J }

val TetrominoShapes = mapOf(
    PieceType.I to listOf(Point.of(-1, 0), Point.of(0, 0), Point.of(1, 0), Point.of(2, 0)),
    PieceType.J to listOf(Point.of(-1, -1), Point.of(-1, 0), Point.of(0, 0), Point.of(1, 0)),
    PieceType.L to listOf(Point.of(1, -1), Point.of(-1, 0), Point.of(0, 0), Point.of(1, 0)),
    PieceType.O to listOf(Point.of(0, 0), Point.of(1, 0), Point.of(0, 1), Point.of(1, 1)),
    PieceType.S to listOf(Point.of(0, 0), Point.of(1, 0), Point.of(-1, 1), Point.of(0, 1)),
    PieceType.T to listOf(Point.of(0, -1), Point.of(-1, 0), Point.of(0, 0), Point.of(1, 0)),
    PieceType.Z to listOf(Point.of(-1, 0), Point.of(0, 0), Point.of(0, 1), Point.of(1, 1))
)

val PieceColorIndices = mapOf(
    PieceType.I to 0,
    PieceType.J to 1,
    PieceType.L to 2,
    PieceType.O to 3,
    PieceType.S to 4,
    PieceType.T to 5,
    PieceType.Z to 6
)

/**
 * Super Rotation System (SRS) Official Guideline Kick Tables
 *
 * Coordinates are mapped to screen space where +X is right, +Y is down.
 * Kick transitions: 0->1, 1->2, 2->3, 3->0 (Clockwise).
 */
private val JLSTZ_KICK_TABLE = mapOf(
    (0 to 1) to listOf(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
    (1 to 2) to listOf(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
    (2 to 3) to listOf(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
    (3 to 0) to listOf(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2)
)

private val I_KICK_TABLE = mapOf(
    (0 to 1) to listOf(0 to 0, -2 to 0, 1 to 0, -2 to 1, 1 to -2),
    (1 to 2) to listOf(0 to 0, -1 to 0, 2 to 0, -1 to -2, 2 to 1),
    (2 to 3) to listOf(0 to 0, 2 to 0, -1 to 0, 2 to -1, -1 to 2),
    (3 to 0) to listOf(0 to 0, 1 to 0, -2 to 0, 1 to 2, -2 to -1)
)

/**
 * Attempts rotation using standard SRS kick offsets.
 */
fun Spirit.tryRotate(blockSet: Set<Pair<Int, Int>>, matrix: Pair<Int, Int>): Spirit? {
    if (pieceType == PieceType.O) return this
    val fromState = rotationState
    val toState = (rotationState + 1) % 4
    val rotated = rotateShape()

    val kicks = if (pieceType == PieceType.I) {
        I_KICK_TABLE[fromState to toState] ?: listOf(0 to 0)
    } else {
        JLSTZ_KICK_TABLE[fromState to toState] ?: listOf(0 to 0)
    }

    for (kick in kicks) {
        val kicked = rotated.moveBy(kick)
        if (kicked.isValidInMatrix(blockSet, matrix)) {
            return kicked
        }
    }
    return null
}

fun Spirit.isValidInMatrix(blockSet: Set<Pair<Int, Int>>, matrix: Pair<Int, Int>): Boolean {
    return location.all { pt ->
        val x = pt.x.toInt()
        val y = pt.y.toInt()
        x in 0 until matrix.first && y in 0 until matrix.second && !blockSet.contains(x to y)
    }
}

fun Spirit.isValidInMatrix(blocks: List<Brick>, matrix: Pair<Int, Int>): Boolean {
    val blockSet = blocks.map { it.location.x.toInt() to it.location.y.toInt() }.toSet()
    return isValidInMatrix(blockSet, matrix)
}

fun generate7Bag(matrix: Pair<Int, Int>): List<Spirit> {
    val spawnX = matrix.first / 2 - 1
    return PieceType.entries.shuffled().map { type ->
        Spirit(
            shape = TetrominoShapes.getValue(type),
            offset = Point.of(spawnX, 1),
            colorIndex = PieceColorIndices.getValue(type),
            pieceType = type,
            rotationState = 0
        )
    }
}
