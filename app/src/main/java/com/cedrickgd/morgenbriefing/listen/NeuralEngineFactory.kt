package com.cedrickgd.morgenbriefing.listen

/**
 * The speech library behind natural voices. This build ships none (the sherpa-onnx runtime that
 * reads Piper voices links espeak-ng, which is GPL-3.0), so natural voices stay hidden.
 */
internal fun createNeuralEngine(): NeuralEngine? = null
