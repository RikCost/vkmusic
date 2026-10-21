package com.vkmusic.ui.components

import android.content.Context
import android.content.Intent

fun Context.shareText(text: String, title: String = "Поделиться") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
