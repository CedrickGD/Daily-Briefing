# kotlinx.serialization ships its own consumer rules; keep our @Serializable models' companions explicit
# so R8 full mode never strips generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.cedrickgd.morgenbriefing.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cedrickgd.morgenbriefing.**$$serializer { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp optional platform classes
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Readability4J logs through slf4j-api without a binding
-dontwarn org.slf4j.**

# sherpa-onnx: the native library reads the TTS config objects field by field (JNI GetFieldID)
# and builds GeneratedAudio itself, so these classes keep their names and members.
-keep class com.k2fsa.sherpa.onnx.OfflineTts* { *; }
-keep class com.k2fsa.sherpa.onnx.GeneratedAudio { *; }
-keep class com.k2fsa.sherpa.onnx.GenerationConfig { *; }
# The native library calls the audio callback by name and signature (invoke([F)Ljava/lang/Integer;).
-keep class com.cedrickgd.morgenbriefing.listen.SamplesCallback { *; }
