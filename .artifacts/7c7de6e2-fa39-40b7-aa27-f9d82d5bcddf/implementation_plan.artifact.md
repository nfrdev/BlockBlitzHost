# UI Improvements for Block Blitz

This plan addresses the time display wrapping issue and provides visual enhancements for all game modes to improve the overall gaming experience.

## User Review Required

> [!NOTE]
> The time wrapping issue (e.g., "01:57" appearing as "01:5" and "7") will be fixed by preventing text wrapping and slightly adjusting font sizes if necessary.

## Proposed Changes

### Core UI Fixes

#### [MODIFY] [BlockBlitzScreen.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/BlockBlitzScreen.kt)
- Update `CompactStatItem` to prevent text wrapping by adding `maxLines = 1` and `softWrap = false` to the value text.
- Use `auto-sizing` logic or slightly reduced font size for values to ensure they fit on smaller screens.
- Enhance the `BlockBlitzHeader` (inlined in the main Column) to use mode-specific colors for the badge.

### Mode-Specific Enhancements

#### [MODIFY] [Utils.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/Utils.kt)
- Add a `color` property to `GameMode` to define a unique accent for each mode.
    - **Marathon**: Purple (`0xFF8B5CF6`)
    - **Blitz**: Cyan (`0xFF06B6D4`)
    - **Zen**: Emerald (`0xFF10B981`)
    - **Daily**: Amber (`0xFFF59E0B`)

#### [MODIFY] [BlockBlitzScreen.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/BlockBlitzScreen.kt)
- Apply mode-specific colors to the mode badge at the top.
- Add a subtle background glow behind the game board that matches the current mode's color.
- Adjust the `CompactStatItem` layout to handle the "BEST" score more gracefully, as it can be large.

## Verification Plan

### Automated Tests
- Run Compose Previews for different game modes to verify badge colors and background glows.
- Test with long score/time strings to ensure no wrapping occurs.

### Manual Verification
- Deploy to a device/emulator.
- Switch between modes (Marathon, Blitz, Zen, Daily) and verify:
    - The top badge color changes correctly.
    - The "TIME" in Blitz mode is displayed correctly without wrapping.
    - The overall UI feels more dynamic and mode-aware.
