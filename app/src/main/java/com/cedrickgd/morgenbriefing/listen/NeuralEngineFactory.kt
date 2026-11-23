package com.cedrickgd.morgenbriefing.listen

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * The speech library behind natural voices: sherpa-onnx 1.13.8 (Apache-2.0, ONNX Runtime linked
 * statically; MIT), whose native library contains espeak-ng (GPL-3.0) for Piper's phonemes.
 * `app/libs/sherpa-onnx-tts-1.13.8.aar` is the release's static-link AAR reduced to arm64-v8a,
 * armeabi-v7a and x86_64 (the build keeps x86_64 for debug only). Throws when the library is
 * missing for this phone's ABI; [NeuralRuntime] then hides natural voices.
 */
internal fun createNeuralEngine(): NeuralEngine {
    System.loadLibrary("sherpa-onnx-jni")
    return SherpaEngine
}

private object SherpaEngine : NeuralEngine {
    override fun open(voice: NeuralVoice, files: NeuralVoiceFiles): NeuralSynth {
        val config = OfflineTtsConfig().apply {
            model = OfflineTtsModelConfig().apply {
                vits = OfflineTtsVitsModelConfig().apply {
                    model = files.model.path
                    tokens = files.tokens.path
                    dataDir = files.dataDir.path
                }
                // Two cores keep up with real time on budget phones and leave the rest to the UI.
                numThreads = 2
                provider = "cpu"
            }
            // One sentence per call: NeuralSpeaker already hands over single sentences.
            maxNumSentences = 1
        }
        return SherpaSynth(OfflineTts(config = config), voice.speaker)
    }
}

private class SherpaSynth(private val tts: OfflineTts, private val speaker: Int) : NeuralSynth {
    override val sampleRate: Int = tts.sampleRate()

    override fun generate(text: String, speed: Float, onSamples: (FloatArray) -> Boolean) {
        tts.generateWithCallback(text, speaker, speed, SamplesCallback(onSamples))
    }

    override fun release() = tts.release()
}

/**
 * The callback the native library calls per chunk of audio. It looks the method up by its JVM
 * signature, `invoke([F)Ljava/lang/Integer;`, which a Kotlin lambda (compiled to invokedynamic)
 * does not have: it only has the erased `invoke(Object)`, and the call aborts the process. A real
 * class gets the typed method; ProGuard keeps it (`proguard-rules.pro`).
 */
internal class SamplesCallback(private val onSamples: (FloatArray) -> Boolean) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = if (onSamples(samples)) 1 else 0
}
