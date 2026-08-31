# 🚀 BlockBlitz Release Guide

Follow these steps whenever you want to release a new version of the app and trigger the auto-update system for existing users.

---

## 📋 Step-by-Step Release Checklist

### Step 1: Bump App Version
Open [`app/build.gradle.kts`](file:///app/build.gradle.kts) and update the version configuration:
* **`versionCode`**: Must be increased by `1` (e.g., `2` ➔ `3`). This is what the app uses to determine if an update is available.
* **`versionName`**: A human-readable label (e.g., `"1.1.0"` ➔ `"1.2.0"`).

```kotlin
defaultConfig {
    applicationId = "com.nfrdev.blockblitzhost"
    minSdk = 23
    targetSdk = 37
    versionCode = 3          // 👈 Update this
    versionName = "1.2.0"    // 👈 Update this
}
```

---

### Step 2: Build the Signed Release APK
Run the following command in the project root terminal:
```powershell
.\gradlew assembleRelease
```
The output APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

---

### Step 3: Get the APK Checksum (SHA-256)
Run this command in PowerShell to get the security checksum of your newly generated APK:
```powershell
(Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256).Hash
```
Copy the hash output (it will be a long string of letters and numbers).

---

### Step 4: Create a GitHub Release
1. Go to your GitHub Repository ➔ **Releases** ➔ **Draft a new release**.
2. Create a new tag matching your version name (e.g., `v1.2.0`).
3. Drag & drop the newly built `app-release.apk` file into the asset upload area.
4. Publish the release.

---

### Step 5: Update `version.json`
Open [`version.json`](file:///version.json) in your project root and update it to match the new release:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/nfrdev/BlockBlitzHost/releases/download/v1.2.0/app-release.apk",
  "updateMessage": "Write description of new changes here!",
  "forceUpdate": false,
  "sha256": "PASTE_THE_SHA256_HASH_HERE"
}
```

* **`forceUpdate`**: Set to `true` if you want to lock the app and force users to update immediately, or `false` to allow skipping.

---

### Step 6: Commit and Push
Stage your changes, commit, and push them to your repository:
```powershell
git add app/build.gradle.kts version.json
git commit -m "Release v1.2.0"
git push
```

Once the push finishes, the new update will be live! 🚀
