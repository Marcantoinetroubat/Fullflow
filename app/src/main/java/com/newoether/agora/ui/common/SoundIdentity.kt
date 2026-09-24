package com.newoether.agora.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.newoether.agora.AgoraApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

object SoundIdentity {

    enum class SoundType {
        SEND,      // Fast frequency slide down (soft whoosh)
        RECEIVE,   // Beautiful double chime (bell)
        BOOKMARK,  // Short crisp click
    }

    /**
     * Plays a procedural sound based on mathematical sine waves.
     * 0 bytes assets size, fully customizable, high-fidelity.
     */
    fun playSound(context: Context, type: SoundType) {
        val app = context.applicationContext as? AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        
        // Respect the dedicated sounds toggle (Paramètres → Apparence → Sons d'interface).
        val soundsEnabled = repo?.soundsEnabled?.value ?: true
        if (!soundsEnabled) return

        Thread {
            try {
                val sampleRate = 22050
                val bufferSize = sampleRate / 2 // up to 0.5 seconds max
                val audioData = ShortArray(bufferSize)

                var actualLen = 0

                when (type) {
                    SoundType.SEND -> {
                        // Whoosh / sweep down
                        val duration = 0.15f // 150 ms
                        actualLen = (sampleRate * duration).toInt()
                        for (i in 0 until actualLen) {
                            val t = i.toFloat() / sampleRate
                            // Sweep from 600 Hz down to 200 Hz
                            val freq = 600f - 400f * (t / duration)
                            audioData[i] = (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.15).toInt().toShort()
                        }
                    }
                    SoundType.RECEIVE -> {
                        // Chime bell (two notes: A4 440Hz -> A5 880Hz)
                        val duration = 0.35f // 350 ms
                        actualLen = (sampleRate * duration).toInt()
                        val transitionPoint = actualLen / 3
                        for (i in 0 until actualLen) {
                            val t = i.toFloat() / sampleRate
                            val isFirstNote = i < transitionPoint
                            val freq = if (isFirstNote) 523.25f else 659.25f // C5 -> E5 harmonious chime
                            
                            // Exponential decay envelope
                            val envelope = if (isFirstNote) {
                                1.0f - (i.toFloat() / transitionPoint)
                            } else {
                                1.0f - ((i - transitionPoint).toFloat() / (actualLen - transitionPoint))
                            }
                            
                            audioData[i] = (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.25 * envelope).toInt().toShort()
                        }
                    }
                    SoundType.BOOKMARK -> {
                        // Crisp tactile click (high freq)
                        val duration = 0.03f // 30 ms
                        actualLen = (sampleRate * duration).toInt()
                        for (i in 0 until actualLen) {
                            val t = i.toFloat() / sampleRate
                            val freq = 1200f
                            val envelope = 1.0f - (i.toFloat() / actualLen)
                            audioData[i] = (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.20 * envelope).toInt().toShort()
                        }
                    }
                }

                // Clamp the static buffer to the platform minimum: a buffer smaller than
                // getMinBufferSize() makes Builder.build() throw (silently swallowed below,
                // i.e. no sound at all on many devices — notably the 30 ms BOOKMARK click).
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val safeMinBytes = if (minBufferBytes > 0) minBufferBytes else actualLen * 2
                // Round up to an even byte count (16-bit frames).
                val bufferSizeInBytes = (maxOf(actualLen * 2, safeMinBytes) + 1) / 2 * 2
                val framesToWrite = bufferSizeInBytes / 2
                val paddedData = if (framesToWrite > actualLen) {
                    audioData.copyOf(framesToWrite) // zero-padded tail = silence
                } else {
                    audioData
                }

                // Play AudioTrack
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(paddedData, 0, framesToWrite)
                track.play()
                
                // Sleep and release
                Thread.sleep((durationSeconds(type) * 1000).toLong() + 50)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Fail-safe
            }
        }.start()
    }

    private fun durationSeconds(type: SoundType): Float = when (type) {
        SoundType.SEND -> 0.15f
        SoundType.RECEIVE -> 0.35f
        SoundType.BOOKMARK -> 0.03f
    }
}
