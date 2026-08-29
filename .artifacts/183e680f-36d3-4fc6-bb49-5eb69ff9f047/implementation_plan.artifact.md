# Notification System Implementation Plan

This plan implements three types of notifications to improve user retention and engagement: Daily Challenge reminders, Inactivity/High Score re-engagement, and Achievement celebrations.

## User Review Required

> [!IMPORTANT]
> The app will now request the `POST_NOTIFICATIONS` permission on startup for users on Android 13 (API 33) and above. Without this, notifications will not be displayed.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/build.gradle.kts)
- Add `androidx.work:work-runtime-ktx:2.11.2` dependency for scheduling background tasks.

---

### Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/AndroidManifest.xml)
- Add `POST_NOTIFICATIONS` permission.

---

### Notification Infrastructure

#### [NEW] [NotificationHelper.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/notifications/NotificationHelper.kt)
- Create notification channels: `Daily Challenge`, `Reminders`, and `Achievements`.
- Helper methods to post notifications with high score and achievement details.

#### [NEW] [DailyChallengeWorker.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/notifications/DailyChallengeWorker.kt)
- Scheduled to check for new daily challenges and notify the user.

#### [NEW] [InactivityWorker.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/notifications/InactivityWorker.kt)
- Scheduled to run after 3 days of inactivity to remind the user of their high score.

---

### Logic Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/MainActivity.kt)
- Add logic to request notification permission using `ActivityResultLauncher`.
- Initialize `NotificationHelper` and schedule initial workers.

#### [MODIFY] [BlockBlitzViewModel.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/blockblitz/BlockBlitzViewModel.kt)
- Trigger achievement notifications when milestones are reached.
- Reset the inactivity timer whenever a game session ends.

## Verification Plan

### Automated Tests
- N/A (Manual verification on device/emulator is preferred for UI/System interactions).

### Manual Verification
- Deploy to an emulator running Android 13+.
- Verify the permission dialog appears.
- Force-run the `DailyChallengeWorker` via ADB to see the notification.
- Verify that finishing a game with a new achievement triggers a notification.
- Verify that high score is correctly displayed in the inactivity notification.
