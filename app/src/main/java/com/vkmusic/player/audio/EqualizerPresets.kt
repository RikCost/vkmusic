package com.vkmusic.player.audio

/**
 * Полосы и пресеты эквалайзера Meridius: 18 peaking-фильтров с Q=1
 * (renderer `createFrequencies`, значения пресетов в дБ — из `dist/renderer/presets.json`).
 */
object EqualizerPresets {
    const val CUSTOM = "Custom"
    const val Q = 1.0
    const val MIN_DB = -15f
    const val MAX_DB = 15f

    val frequencies = intArrayOf(31, 63, 87, 125, 175, 250, 350, 500, 700, 1000, 1400, 2000, 2800, 4000, 5600, 8000, 11200, 16000)

    val flat: List<Float> = List(frequencies.size) { 0f }

    val presets: Map<String, List<Float>> = linkedMapOf(
        "Alternative" to listOf(7.4f, 6.6f, 5.1f, 4.2f, 2.9f, 2.1f, 0.8f, 0.0f, -1.3f, -2.1f, -0.8f, 0.0f, 1.3f, 2.1f, 3.4f, 4.2f, 5.6f, 6.6f),
        "Ballad" to listOf(-4.7f, -3.2f, 0.0f, 1.6f, 3.2f, 3.9f, 4.7f, 3.9f, 3.2f, 2.4f, 0.0f, -1.6f, -3.2f, -3.2f, -3.2f, -4.2f, 1.6f, -4.7f),
        "Classic" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.9f, -5.8f, -5.8f, -6.0f, -8.1f),
        "Club" to listOf(0.0f, 0.0f, 0.0f, 0.9f, 1.8f, 2.1f, 2.4f, 3.3f, 4.2f, 4.5f, 3.6f, 2.4f, 2.1f, 1.5f, 0.9f, 0.0f, 0.0f, 0.0f),
        "Dance" to listOf(9.0f, 6.6f, 4.8f, 2.1f, 0.6f, 0.0f, 0.0f, -1.2f, -2.1f, -2.7f, -3.9f, -4.2f, -4.2f, -4.2f, -4.5f, -4.2f, 0.0f, 0.0f),
        "Full Bass" to listOf(7.4f, 7.2f, 7.2f, 7.2f, 5.7f, 4.2f, 0.9f, -0.3f, -1.8f, -3.0f, -4.2f, -4.5f, -5.1f, -6.0f, -6.6f, -7.2f, -8.4f, -9.0f),
        "Full Bass & Treble" to listOf(7.4f, 7.2f, 7.2f, 6.6f, 3.9f, 0.0f, -6.0f, -2.1f, -1.5f, 0.0f, 1.2f, 2.1f, 4.2f, 6.0f, 7.8f, 8.4f, 9.2f, 9.2f),
        "Full Treble" to listOf(-7.9f, -7.9f, -7.9f, -7.8f, -6.6f, -6.0f, -4.5f, -2.4f, 0.6f, 2.7f, 5.1f, 8.1f, 10.5f, 12.3f, 12.9f, 12.9f, 13.1f, 12.9f),
        "Headphones" to listOf(6.7f, 6.7f, 6.7f, 6.7f, 6.6f, 4.7f, 2.4f, -0.5f, -2.3f, -1.7f, -1.2f, -0.4f, 0.7f, 1.6f, 2.6f, 3.9f, 5.5f, 9.0f),
        "Heavy Metal" to listOf(-2.1f, 2.4f, 3.9f, 3.0f, 0.0f, -3.9f, -4.8f, -5.1f, -4.8f, -4.8f, -2.4f, -0.9f, 0.6f, 2.7f, 5.7f, 6.6f, 6.7f, 2.5f),
        "Hip-Hop" to listOf(5.0f, 4.0f, 2.5f, 1.5f, 2.1f, 2.5f, 0.1f, -1.5f, -1.5f, -1.5f, 0.1f, 1.2f, -0.1f, -1.0f, 0.8f, 2.0f, 2.3f, 2.5f),
        "Industrial" to listOf(-2.1f, -2.4f, 0.9f, 3.0f, 2.1f, 1.5f, 0.6f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.6f, 2.7f, 5.0f, 6.6f, 4.1f, 2.5f),
        "Jazz" to listOf(3.0f, 6.3f, 5.1f, 3.6f, 1.8f, -3.9f, -4.8f, -5.1f, -2.1f, 1.2f, 4.5f, 9.0f, 3.0f, -1.8f, -4.5f, -2.4f, -0.5f, 2.5f),
        "Large Hall" to listOf(6.7f, 6.7f, 6.7f, 6.7f, 6.6f, 4.9f, 3.6f, 3.6f, 2.7f, 0.0f, -0.6f, -1.6f, -2.9f, -3.2f, -3.2f, -3.2f, -3.2f, 0.0f),
        "Live" to listOf(-4.2f, -3.0f, -1.5f, 3.3f, 4.5f, 5.1f, 4.8f, 4.8f, 5.1f, 4.2f, 3.6f, 3.0f, 2.4f, 2.4f, 2.1f, 2.1f, 1.8f, 1.5f),
        "Party" to listOf(5.4f, 5.4f, 5.4f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.4f, 5.4f),
        "Pop" to listOf(-1.6f, 0.6f, 3.9f, 5.4f, 5.5f, 4.5f, 2.1f, 0.9f, -0.6f, -1.5f, -1.5f, -1.8f, -2.1f, -2.1f, -2.7f, -2.1f, -2.1f, -0.3f),
        "Rap" to listOf(0.0f, 4.2f, 5.3f, 2.7f, 0.0f, -3.9f, -4.5f, -3.3f, 0.3f, 1.5f, -1.8f, -5.4f, -0.6f, 2.7f, 4.5f, 5.1f, 2.4f, 0.0f),
        "Reggae" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.3f, -1.0f, -2.6f, -2.7f, 0.0f, 0.9f, 2.2f, 4.0f, 4.4f, 4.4f, 2.9f, 0.6f, 0.0f),
        "Rock" to listOf(5.4f, 4.5f, 3.6f, -3.6f, -6.3f, -6.6f, -3.6f, -2.7f, -0.3f, 2.1f, 4.5f, 6.0f, 6.9f, 7.5f, 7.8f, 7.8f, 7.8f, 8.1f),
        "Ska" to listOf(-1.3f, -1.7f, -2.1f, -2.6f, -3.2f, -3.0f, -2.6f, -1.4f, 0.2f, 2.5f, 2.7f, 3.1f, 3.5f, 4.2f, 5.2f, 5.6f, 5.8f, 5.9f),
        "Soft" to listOf(3.9f, 2.4f, 0.9f, 0.6f, -1.8f, -3.0f, -2.1f, -1.5f, 0.6f, 2.1f, 4.5f, 6.0f, 6.9f, 7.5f, 8.1f, 8.1f, 8.4f, 8.1f),
        "Soft Rock" to listOf(1.8f, 2.1f, 2.1f, 2.1f, 0.9f, -0.3f, -1.5f, -2.7f, -4.5f, -3.9f, -4.5f, -3.9f, -3.9f, -2.1f, 0.0f, 2.4f, 5.4f, 8.1f),
        "Techno" to listOf(5.1f, 4.8f, 4.5f, 4.1f, 3.5f, 1.5f, -0.5f, -2.4f, -3.5f, -3.2f, -2.6f, -1.6f, -0.3f, 1.6f, 4.2f, 5.2f, 5.8f, 5.5f),
        "Vocal" to listOf(-4.8f, -4.5f, -3.9f, -2.1f, -0.3f, 1.2f, 1.8f, 3.6f, 6.6f, 9.0f, 6.9f, 4.5f, 2.4f, 0.3f, -0.9f, -2.1f, -2.7f, -3.0f),
    )

    fun label(frequency: Int): String = if (frequency >= 1000) "${frequency / 1000.0}".removeSuffix(".0") + "k" else "$frequency"
}
