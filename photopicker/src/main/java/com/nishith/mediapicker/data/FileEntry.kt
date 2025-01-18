package com.nishith.mediapicker.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileEntry(
    var id:Int,
    var uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    var isImageSelected :Boolean =false
) : Parcelable