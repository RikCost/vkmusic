package com.vkmusic.data.model

data class Community(
    val id: Long,
    val name: String,
    val photoUrl: String? = null,
) {
    // В audio.* стена и музыка сообщества адресуются отрицательным owner_id.
    val ownerId: Long get() = -id
}

data class Profile(
    val id: Long,
    val name: String,
    val screenName: String? = null,
    val photoUrl: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
)

data class LyricLine(val text: String, val startMs: Long? = null)

data class MixCategory(
    val id: String,
    val title: String,
    val options: List<MixOption>,
)

data class MixOption(val id: String, val title: String)
