package com.nfrdev.blockblitzhost.blockblitz

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockBlitzEngineTest {

    @Test
    fun `line clear scenario produces score and board update`() = runBlocking {
        val engine = GameEngine(
            GameEngine.ViewState(
                bricks = (0 until 11).map { Brick(Point.of(it, 23)) },
                spirit = Spirit(
                    shape = listOf(Point.of(0, 0)),
                    offset = Point.of(11, 22)
                ),
                spiritReserve = emptyList(),
                matrix = 12 to 24,
                gameStatus = GameStatus.Running,
                score = 0,
                line = 0
            )
        )

        engine.dispatch(Action.GameTick)
        delay(50)
        engine.dispatch(Action.GameTick)
        delay(700)

        val state = engine.viewState.value
        assertEquals(GameStatus.Running, state.gameStatus)
        assertEquals(112, state.score)
        assertEquals(1, state.line)
        assertEquals(0, state.bricks.count { it.location.y == 23f })
    }
}
