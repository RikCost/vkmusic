package com.vkmusic.data.model

data class Friend(
    val id: Long,
    val name: String,
    val photoUrl: String? = null,
    val trackCount: Int = 0,
)
