package com.fruit.ffmpeggui.data

import android.net.Uri

data class SelectedMediaFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)
