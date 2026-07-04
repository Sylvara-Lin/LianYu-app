# ================================================================
# LianYu ProGuard/R8 Rules — Production Release
# ================================================================

# -overloadaggressively  # REMOVED: breaks Kotlin metadata → R8 produces invalid bytecode → SIGABRT
# -mergeinterfacesaggressively  # REMOVED: aggressive interface merging corrupts class hierarchy → SIGABRT on initWeChat
# -allowaccessmodification      # REMOVED: conservative — interacts badly with Kotlin inline/companion access
-repackageclasses
-renamesourcefileattribute ""
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# Prevent -repackageclasses from moving Dex2C-whitelisted classes out of
-keeppackagenames com.lianyu.ai.security

# Kotlin metadata: R8 must be able to parse these for correct optimization
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.metadata.**
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Exceptions,Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations

# One-Piece Shell: only keep JNI/manifest entry classes whose names must be stable.
# All other security/* classes are now obfuscated — their names are not visible in DEX strings.
-keep class com.lianyu.ai.security.LianYuShellApplication { *; }
-keep class com.lianyu.ai.security.G0 { *; }
-keep class com.lianyu.ai.security.SActivity { *; }
-keep class com.lianyu.ai.security.SecurityOrchestrator { *; }
-keep class com.lianyu.ai.security.Sm4Cipher { *; }
-keep class com.lianyu.ai.security.SecurityGuard { *; }

# P2-15: 阻止 R8 内联 Dex2C 白名单方法，确保转译器能找到字节码
# 使用 <methods> 匹配所有方法（ProGuard 不支持 *** 通配符）
    <methods>;
}
-keepclassmembers class com.lianyu.ai.security.SecurityOrchestrator {
    <methods>;
}
-keepclassmembers class com.lianyu.ai.security.Sm4Cipher {
    <methods>;
}
    <methods>;
}
-keepclassmembers class com.lianyu.ai.security.SecurityGuard {
    <methods>;
}
    <methods>;
}

# Strip Android logging in release builds.
# EXCEPTION: SecureLog.critical() uses Log.wtf — keep the class intact.
-keep class com.lianyu.ai.common.SecureLog { *; }
-keep class com.lianyu.ai.common.RemoteKeyProvider { *; }
-keep class com.lianyu.ai.network.CertificatePins { *; }

# 🔒 ServiceRegistry: reflection-based register()/get() — must survive obfuscation
-keep class com.lianyu.ai.domain.ServiceRegistry { *; }
-keepclassmembers class com.lianyu.ai.domain.ServiceRegistry {
    public static <methods>;
}

# 🔒 feature:notification: CompanionKeepAliveService + BootReceiver declared in AndroidManifest
-keep class com.lianyu.ai.feature.notification.** { *; }

# 🔒 Push: PushManager imported in LianYuApplication, routes to vendor Push SDKs
-keep class com.lianyu.ai.push.PushManager { *; }

# kotlinx.serialization: preserve serializers for reflection-based adapter lookup
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lianyu.ai.**$$serializer { *; }
-keepclassmembers class com.lianyu.ai.** {
    *** Companion;
}
-keepclasseswithmembers class com.lianyu.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Android manifest entry points must stay loadable by class name.
# R8 updates manifest class references when obfuscation renames them.
-keep class com.lianyu.ai.LianYuApplication { *; }
-keep class com.lianyu.ai.MainActivity { *; }
-keep,allowobfuscation class * extends android.app.Service { *; }
-keep,allowobfuscation class * extends android.content.BroadcastReceiver { *; }
-keep,allowobfuscation class * extends androidx.work.Worker { *; }
-keep,allowobfuscation class * extends androidx.work.CoroutineWorker { *; }

# symbols. Keep class names, member names, and descriptors intact.

# ================================================================
# Google AI Edge LiteRT-LM (litertlm-android) — CRITICAL
# ================================================================
# R8 does NOT understand native library loading side effects and WILL:
#   1. Strip System.loadLibrary() calls from static initializers
#   2. Rename JNI classes → native method names in .so won't match
#   3. Remove "unused" native methods
# This causes: No implementation found → SIGILL → instant crash.
# Keep ALL litertlm classes, members, and native method signatures intact.
-keep,includedescriptorclasses class com.google.ai.edge.litertlm.** { *; }
-keep,includedescriptorclasses class com.google.ai.edge.litertlm.NativeLibraryLoader { *; }
-keep,includedescriptorclasses class com.google.ai.edge.litertlm.LiteRtLmJni { *; }

# Prevent R8 from stripping System.loadLibrary calls in static initializers.
# Even with -keep, R8 may optimize away side-effect-free-looking code paths
# that include loadLibrary. This directive preserves all native method stubs.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Room: keep annotations and generated metadata, but allow class/interface names to be obfuscated.
-keep,allowobfuscation class com.lianyu.ai.database.AppDatabase { *; }
-keep,allowobfuscation @androidx.room.Entity class com.lianyu.ai.database.model.** { *; }
-keep,allowobfuscation interface com.lianyu.ai.database.dao.** { *; }
-keep class androidx.room.** { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers,allowobfuscation class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Retrofit interfaces and HTTP annotations.
-keep,allowobfuscation interface com.lianyu.ai.network.OpenAiApi { *; }
-keep,allowobfuscation interface com.lianyu.ai.network.AnthropicApi { *; }
-keep,allowobfuscation interface com.lianyu.ai.network.GeminiApi { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Kotlin serialization: DTO obfuscation is safe because:
# - Custom serializers use string-based key lookups (no reflection on field names)
# - Auto-generated serializers respect @SerialName annotations for wire names
# - R8 preserves annotations via -keepattributes
# Class names, property names, and method names may all be obfuscated freely.

# Compose runtime needs annotations, not app composable names.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Third-party SDKs that use reflection/native loading.
-keep class com.github.wechat.ilink.sdk.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn androidx.work.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.security.crypto.**

# Crypto: keep JCA classes intact for AES-256-GCM encrypted API calls
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-dontwarn javax.crypto.**

# ViewModel constructors are created reflectively by AndroidX factories; allow
# class/method obfuscation while preserving constructors.
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# WeChat module — keep initialization path intact; R8 aggressive optimizations
# (mergeinterfacesaggressively/overloadaggressively) corrupt static initializers
# in these classes, causing SIGABRT on startup (LianYuApplication.initWeChat).
-keep class com.lianyu.ai.feature.wechat.** { *; }
-keep class com.lianyu.ai.feature.wechat.data.** { *; }
-keep class com.lianyu.ai.feature.wechat.service.** { *; }

# DEX padding — must survive R8 to push method count past 64K, forcing multi-dex
-keep class com.lianyu.ai.internal.DexPadding { *; }

# Android framework conventions.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.lianyu.ai.R$* { *; }

# Enum converters need value lookup; names are persisted in Room values.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================================================
# Vendor Push SDKs (OPPO / vivo / Xiaomi / Huawei)
# ================================================================
# OPPO / vivo / Xiaomi SDKs are bundled as local aars and accessed via reflection.
# Keep their class names and members so Class.forName / getMethod work in release builds.
-keep class com.heytap.msp.push.** { *; }
-keep class com.coloros.mcs.** { *; }
-keep class com.vivo.push.** { *; }
-keep class com.xiaomi.mipush.sdk.** { *; }
-keep class com.xiaomi.push.** { *; }
-keep class com.huawei.hms.push.** { *; }
-dontwarn com.heytap.msp.push.**
-dontwarn com.coloros.mcs.**
-dontwarn com.vivo.push.**
-dontwarn com.xiaomi.mipush.sdk.**
-dontwarn com.xiaomi.push.**
# ═══════════════════════════════════════════════════════════════
# Ultimate Shell — protect shell DEX + assets from R8/strip
# ═══════════════════════════════════════════════════════════════

# Keep shell bootstrapping classes (in shell DEX, referenced by manifest)
-keep class com.lianyu.ai.security.SActivity { *; }
-keep class com.lianyu.ai.security.MethodRecoveryEngine { *; }
-keep class com.lianyu.ai.security.G0 { *; }

# Keep MainActivity (manifest LAUNCHER → must be resolvable by ClassLoader)
-keep class com.lianyu.ai.MainActivity { *; }
-keep class com.lianyu.ai.LianYuApplication { *; }

# Keep all native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep AndroidX runtime (for WorkManager initialization in shell)
-keep class androidx.work.WorkManager { *; }
-keep class androidx.work.Configuration { *; }
-keep class androidx.work.Configuration$Builder { *; }
-keep class androidx.work.impl.WorkManagerInitializer { *; }

# Keep InMemoryDexClassLoader (used by shell at runtime)
-keep class dalvik.system.InMemoryDexClassLoader { *; }
-keep class dalvik.system.BaseDexClassLoader { *; }
-keep class dalvik.system.DexPathList { *; }
-keep class dalvik.system.DexPathList$Element { *; }

# ═══════════════════════════════════════════════════════════════
# WorkManager — preserve reflection-based initialization
# ═══════════════════════════════════════════════════════════════
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepattributes *Annotation*
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.WorkManager { *; }
-keep class androidx.work.Configuration { *; }
-keep class androidx.work.Configuration$Builder { *; }
-keep class androidx.work.impl.WorkManagerInitializer { *; }

-dontwarn com.huawei.hms.**
-dontwarn com.huawei.android.os.**
-dontwarn com.huawei.hianalytics.**
-dontwarn com.huawei.libcore.io.**
-dontwarn org.apache.commons.codec.**

