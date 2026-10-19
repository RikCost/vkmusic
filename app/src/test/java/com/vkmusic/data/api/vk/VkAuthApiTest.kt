package com.vkmusic.data.api.vk

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VkAuthApiTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val server = MockWebServer()
    private lateinit var api: VkAuthApi
    private val credentials = VkCredentials("user@example.test", "not-a-real-password")

    @Before
    fun setUp() {
        server.start()
        api = VkAuthApi(OkHttpClient(), json, server.url("/method/"), server.url("/oauth/token"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `login without configured client id fails fast and makes no network call`() = runTest {
        val unconfigured = VkAuthApi(
            OkHttpClient(), json, server.url("/method/"), server.url("/oauth/token"),
            isConfigured = { false },
        )
        val result = unconfigured.login(credentials)

        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("VkIdConfig"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `parse maps a successful token response`() {
        assertEquals(
            AuthResult.Success("abc", 42),
            api.parse("""{"access_token":"abc","expires_in":0,"user_id":42}"""),
        )
    }

    @Test
    fun `parse maps need_validation to two factor`() {
        assertEquals(
            AuthResult.NeedValidation("sid1", "2fa_app", "+7 *** *** ** 12"),
            api.parse(
                """{"error":"need_validation","validation_type":"2fa_app","validation_sid":"sid1","phone_mask":"+7 *** *** ** 12"}""",
            ),
        )
    }

    @Test
    fun `parse maps need_captcha`() {
        assertEquals(
            AuthResult.NeedCaptcha("777", "https://api.vk.ru/captcha.php?sid=777"),
            api.parse("""{"error":"need_captcha","captcha_sid":"777","captcha_img":"https://api.vk.ru/captcha.php?sid=777"}"""),
        )
    }

    @Test
    fun `parse maps error description and invalid body to failure`() {
        assertEquals(
            AuthResult.Failure("Username or password is incorrect"),
            api.parse("""{"error":"invalid_client","error_description":"Username or password is incorrect"}"""),
        )
        assertEquals(AuthResult.Failure("Некорректный ответ сервера авторизации"), api.parse("<html>"))
    }

    @Test
    fun `validation retry posts code and sid to token endpoint`() = runTest {
        val authorized = configuredApi()
        enqueueAnonymToken()
        server.enqueue(MockResponse.Builder().body("""{"response":{"sid":"sid-1"}}""").build())
        server.enqueue(MockResponse.Builder().body("""{"access_token":"abc","user_id":42}""").build())

        assertEquals(AuthResult.Success("abc", 42), authorized.login(credentials, code = "7777", sid = "sid-1"))

        server.takeRequest()
        server.takeRequest()
        val body = requireNotNull(server.takeRequest().body).utf8()
        assertTrue(body.contains("code=7777"))
        assertTrue(body.contains("sid=sid-1"))
    }

    @Test
    fun `code request hits ecosystem sendOtpEmail with sid and anonymous token`() = runTest {
        val authorized = configuredApi()
        enqueueAnonymToken()
        server.enqueue(MockResponse.Builder().body("""{"response":{"success":1,"info":"email"}}""").build())

        assertEquals(OtpResult.Sent, authorized.sendOtp("sid-1", OtpChannel.Email))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/method/ecosystem.sendOtpEmail", request.url.encodedPath)
        val body = requireNotNull(request.body).utf8()
        assertTrue(body.contains("sid=sid-1"))
        assertTrue(body.contains("access_token=anon-1"))
        assertEquals(VkConfig.ANDROID_CLIENT_VALUE, request.headers[VkConfig.ANDROID_CLIENT_HEADER])
    }

    @Test
    fun `code request reports vk error instead of failing silently`() = runTest {
        val authorized = configuredApi()
        enqueueAnonymToken()
        server.enqueue(
            MockResponse.Builder().body("""{"error":1,"error_code":1,"error_msg":"User confirmation failed"}""").build(),
        )

        assertEquals(OtpResult.Rejected("User confirmation failed"), authorized.sendOtp("sid-1", OtpChannel.Email))
    }

    private fun enqueueAnonymToken() {
        server.enqueue(MockResponse.Builder().body("""{"response":{"token":"anon-1","expired_at":0}}""").build())
    }

    private fun configuredApi() = VkAuthApi(
        OkHttpClient(), json, server.url("/method/"), server.url("/oauth/token"),
        isConfigured = { true },
    )
}
