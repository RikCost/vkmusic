package com.vkmusic.data.api.vk

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// VK отдаёт флаги то как true/false, то как 1/0 — в зависимости от метода и версии API.
object VkBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("VkBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive ?: return decoder.decodeBoolean()
        return primitive.booleanOrNull ?: ((primitive.intOrNull ?: 0) != 0)
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

@Serializable
data class VkItems<T>(
    val count: Int = 0,
    val items: List<T> = emptyList(),
)

@Serializable
data class VkError(
    @SerialName("error_code") val code: Int,
    @SerialName("error_msg") val message: String = "",
    @SerialName("captcha_sid") val captchaSid: String? = null,
    @SerialName("captcha_img") val captchaImg: String? = null,
)

@Serializable
data class AudioDto(
    val id: Long,
    @SerialName("owner_id") val ownerId: Long,
    val artist: String = "",
    val title: String = "",
    val duration: Int = 0,
    val url: String? = null,
    val album: AudioAlbumDto? = null,
    @SerialName("access_key") val accessKey: String? = null,
    @Serializable(VkBooleanSerializer::class) @SerialName("is_explicit") val isExplicit: Boolean = false,
    @Serializable(VkBooleanSerializer::class) @SerialName("has_lyrics") val hasLyrics: Boolean = false,
    @SerialName("main_artists") val mainArtists: List<ArtistDto> = emptyList(),
)

@Serializable
data class AudioAlbumDto(
    val id: Long? = null,
    val title: String? = null,
    val thumb: ThumbDto? = null,
)

@Serializable
data class ThumbDto(
    @SerialName("photo_135") val photo135: String? = null,
    @SerialName("photo_300") val photo300: String? = null,
    @SerialName("photo_600") val photo600: String? = null,
) {
    val best: String? get() = photo600 ?: photo300 ?: photo135
}

@Serializable
data class PlaylistDto(
    val id: Long,
    @SerialName("owner_id") val ownerId: Long,
    val title: String = "",
    val description: String = "",
    val count: Int = 0,
    @SerialName("access_key") val accessKey: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    val photo: ThumbDto? = null,
    val thumbs: List<ThumbDto> = emptyList(),
    @SerialName("main_artists") val mainArtists: List<ArtistDto> = emptyList(),
    val original: PlaylistRefDto? = null,
    val plays: Long = 0,
    @SerialName("update_time") val updateTime: Long = 0,
)

@Serializable
data class ArtistDto(val name: String = "", val id: String? = null)

@Serializable
data class PlaylistRefDto(
    val id: Long,
    @SerialName("owner_id") val ownerId: Long,
    @SerialName("access_key") val accessKey: String? = null,
)

@Serializable
data class UserDto(
    val id: Long,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("photo_100") val photo100: String? = null,
    @SerialName("can_see_audio") val canSeeAudio: Int = 0,
    @SerialName("photo_200") val photo200: String? = null,
    @SerialName("screen_name") val screenName: String? = null,
)

@Serializable
data class GroupDto(
    val id: Long,
    val name: String = "",
    @SerialName("photo_100") val photo100: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("validation_type") val validationType: String? = null,
    @SerialName("validation_sid") val validationSid: String? = null,
    @SerialName("phone_mask") val phoneMask: String? = null,
    @SerialName("captcha_sid") val captchaSid: String? = null,
    @SerialName("captcha_img") val captchaImg: String? = null,
    @SerialName("super_app_token") val superAppToken: String? = null,
)

@Serializable
data class AnonymTokenResponse(
    val token: String? = null,
    @SerialName("expired_at") val expiredAt: Long = 0,
)

@Serializable
data class ValidateAccountResponse(
    val sid: String? = null,
    @SerialName("flow_name") val flowName: String? = null,
    @SerialName("flow_names") val flowNames: List<String> = emptyList(),
    @SerialName("can_skip_password") val canSkipPassword: Boolean = false,
)

@Serializable
data class SendOtpResponse(
    val success: Int = 0,
)
