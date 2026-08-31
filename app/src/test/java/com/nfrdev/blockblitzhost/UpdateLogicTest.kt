package com.nfrdev.blockblitzhost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun `does not show update when installed version already matches remote version`() {
        assertFalse(UpdateManager.shouldShowUpdate(remoteVersionCode = 4, currentVersionCode = 4, skippedVersionCode = 0))
    }

    @Test
    fun `shows update when remote version is newer than installed app`() {
        assertTrue(UpdateManager.shouldShowUpdate(remoteVersionCode = 5, currentVersionCode = 4, skippedVersionCode = 0))
    }

    @Test
    fun `does not show update for same version even if it was not skipped`() {
        assertFalse(UpdateManager.shouldShowUpdate(remoteVersionCode = 5, currentVersionCode = 5, skippedVersionCode = 0))
    }
}
