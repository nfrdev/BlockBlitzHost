package com.nfrdev.blockblitzhost

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val updateMessage: String,
    val sha256: String? = null,
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val minimumRequiredVersionCode: Int = 0,
    val changelog: List<String> = emptyList(),
    val updateType: String = "optional",
    val releaseDate: String = ""
)
