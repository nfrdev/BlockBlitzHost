# Fix Black Screen on Startup

The app exhibits a black screen on startup, likely due to a combination of heavy main thread work in `onCreate` and a potential infinite loop or rendering delay in the animated background. Logcat indicates a `Mutex` contention on the `Default` dispatcher and significant frame skipping (187 frames).

## Proposed Changes

### [MainActivity](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/MainActivity.kt)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/MainActivity.kt)
- Move `SoundUtil.init(this)` from `onCreate` to a `LaunchedEffect` inside `setContent`. This prevents blocking the activity startup.
- Add a safety check in `AnimatedFallingBlocksBackground` to ensure `gridStep` is greater than zero, preventing a potential infinite loop in the `Canvas` drawing logic.

### [Utils](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/Utils.kt)

#### [MODIFY] [Utils.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/Utils.kt)
- Update `SoundUtil.init` to use `context.applicationContext` to avoid potential memory leaks and ensure consistent behavior across lifecycle events.

## Verification Plan

### Automated Tests
- Run existing tests to ensure no regressions in game logic.

### Manual Verification
- Deploy the app to the emulator and verify that the welcome screen appears promptly without a long black screen.
- Verify that sounds still play correctly during gameplay.
- Check Logcat for "Skipped frames" and `Mutex` contention warnings.
