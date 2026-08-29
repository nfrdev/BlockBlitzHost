# GitHub-Based Update System Implementation Plan

This plan outlines the steps to build a free, serverless update system for the Android app using GitHub Releases and a `version.json` file.

## User Review Required

> [!IMPORTANT]
> **Permissions**: This implementation requires `REQUEST_INSTALL_PACKAGES`. Android will prompt the user to allow "Installation from Unknown Sources" for this app.
> **GitHub Setup**: You will need to manually upload a `version.json` and your APK to a GitHub Release for the system to work.

## Proposed Changes

### [Component] Android Manifest & Configuration

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/AndroidManifest.xml)
- Add `INTERNET` permission.
- Add `REQUEST_INSTALL_PACKAGES` permission.
- Declare `FileProvider` to share the downloaded APK with the system installer.

#### [NEW] [file_paths.xml](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/res/xml/file_paths.xml)
- Define the cache/external paths that the `FileProvider` is allowed to share.

---

### [Component] Update Logic

#### [NEW] [UpdateInfo.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/UpdateInfo.kt)
- Kotlin data class for parsing the `version.json` file.

#### [NEW] [UpdateManager.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/UpdateManager.kt)
- `checkUpdate()`: Fetches JSON from GitHub and compares `versionCode`.
- `downloadAndInstall()`: Uses `DownloadManager` to fetch the APK and `FileProvider` to trigger installation.

---

### [Component] UI Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Nafira/OneDrive/Desktop/BlockBlitzHost/app/src/main/java/com/nfrdev/blockblitzhost/MainActivity.kt)
- Initialize `UpdateManager` and trigger a check on app start.
- (Optional) Show a Compose dialog when an update is available.

## Verification Plan

### Manual Verification
1.  **Mock GitHub JSON**: Temporarily point the URL to a local mock or a test Gist with a higher `versionCode`.
2.  **Verify Dialog**: Ensure the app detects the "new" version and shows the update UI.
3.  **Test Download**: Verify `DownloadManager` starts and completes.
4.  **Test Installation**: Confirm the app triggers the System Installer and prompts for "Unknown Sources" permission if not already granted.
