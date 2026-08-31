package com.nfrdev.blockblitzhost.blockblitz

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockBlitzEngineTest {

    @Test
    fun `line clear scenario produces score and board update`() = runBlocking {
        val engine = GameEngine(
            GameEngine.ViewState(
                bricks = (0 until 9).map { Brick(Point.of(it, 19)) },
                spirit = Spirit(
                    shape = listOf(Point.of(0, 0)),
                    offset = Point.of(9, 18),
                    pieceType = PieceType.O
                ),
                spiritReserve = emptyList(),
                matrix = 10 to 20,
                gameStatus = GameStatus.Running,
                score = 0,
                line = 0
            )
        )

        // Move down once to touch bottom line
        engine.dispatch(Action.GameTick)
        delay(50)
        // Next tick triggers lock and line clear; wait for both lock and clear animation delays
        engine.dispatch(Action.GameTick)
        delay(1000)

        val state = engine.viewState.value
        assertEquals(GameStatus.Running, state.gameStatus)
        assertEquals(112, state.score) // 100 * level(1) + 12 piece lock
        assertEquals(1, state.line)
        assertEquals(0, state.bricks.count { it.location.y == 19f })
    }

    @Test
    fun `hard drop immediately locks piece and awards score`() = runBlocking {
        val engine = GameEngine(
            GameEngine.ViewState(
                bricks = emptyList(),
                spirit = Spirit(
                    shape = listOf(Point.of(0, 0)),
                    offset = Point.of(5, 0),
                    pieceType = PieceType.O
                ),
                matrix = 10 to 20,
                gameStatus = GameStatus.Running,
                score = 0
            )
        )

        // Hard drop from y=0 to y=19 (19 cells * 2 = 38 pts + 12 lock = 50 pts)
        engine.dispatch(Action.Drop)
        delay(50)

        val state = engine.viewState.value
        assertEquals(50, state.score)
        assertTrue("Dropped brick should be locked at bottom", state.bricks.any { it.location.y == 19f })
    }

    @Test
    fun `SRS wall kick allows I piece to rotate near left wall`() {
        val blockSet = emptySet<Pair<Int, Int>>()
        val matrix = 10 to 20
        // Vertical I-piece near left wall
        val verticalI = Spirit(
            shape = listOf(Point.of(0, -1), Point.of(0, 0), Point.of(0, 1), Point.of(0, 2)),
            offset = Point.of(0, 5),
            pieceType = PieceType.I,
            rotationState = 1
        )

        val rotated = verticalI.tryRotate(blockSet, matrix)
        assertNotNull("I-piece should successfully kick off the left wall", rotated)
        assertTrue(
            "Rotated I-piece must be completely inside the board",
            rotated!!.location.all { it.x in 0f..9f && it.y in 0f..19f }
        )
    }

    @Test
    fun `pieces cannot occupy negative or overlapping matrix coordinates`() {
        val blockSet = setOf(4 to 0)
        val matrix = 10 to 20
        val overlappingPiece = Spirit(
            shape = listOf(Point.of(0, 0), Point.of(1, 0)),
            offset = Point.of(3, -1),
            pieceType = PieceType.O
        )

        assertTrue("Negative Y cells are invalid and should not overlap the board", !overlappingPiece.isValidInMatrix(blockSet, matrix))
    }

    @Test
    fun `O piece does not drift when rotated`() {
        val blockSet = emptySet<Pair<Int, Int>>()
        val matrix = 10 to 20
        val oPiece = Spirit(
            shape = TetrominoShapes.getValue(PieceType.O),
            offset = Point.of(4, 5),
            pieceType = PieceType.O
        )

        val rotated = oPiece.tryRotate(blockSet, matrix)
        assertNotNull(rotated)
        assertEquals("O piece locations must remain identical", oPiece.location, rotated!!.location)
    }

    @Test
    fun `7-bag generates one of each piece without duplicate counts`() {
        val bag = generate7Bag(10 to 20)
        assertEquals(7, bag.size)
        assertEquals(PieceType.entries.toSet(), bag.map { it.pieceType }.toSet())
    }
}
