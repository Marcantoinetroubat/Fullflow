package com.newoether.agora.studio.tts.kokoro

/**
 * Curated Kokoro voice list for the bundled sherpa-onnx model
 * `kokoro-multi-lang-v1_0` (53 speakers, sid 0..52).
 *
 * Source: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html
 */
data class KokoroVoice(val id: String, val sid: Int, val label: String, val lang: String)

object KokoroVoices {
    const val DEFAULT_VOICE_ID = "ff_siwis"

    val VOICES = listOf(
        KokoroVoice("ff_siwis", 30, "Siwis", "FR"),
        KokoroVoice("af_heart", 3, "Heart", "EN-US"),
        KokoroVoice("af_bella", 2, "Bella", "EN-US"),
        KokoroVoice("af_sky", 10, "Sky", "EN-US"),
        KokoroVoice("am_adam", 11, "Adam", "EN-US"),
        KokoroVoice("am_onyx", 17, "Onyx", "EN-US"),
        KokoroVoice("bf_emma", 21, "Emma", "EN-GB"),
        KokoroVoice("bm_george", 26, "George", "EN-GB"),
        KokoroVoice("ef_dora", 28, "Dora", "ES"),
        KokoroVoice("if_sara", 35, "Sara", "IT"),
        KokoroVoice("jf_alpha", 37, "Alpha", "JA"),
        KokoroVoice("pf_dora", 42, "Dora", "PT"),
    )

    fun sidFor(id: String): Int =
        VOICES.find { it.id == id }?.sid ?: VOICES.first { it.id == DEFAULT_VOICE_ID }.sid
}
