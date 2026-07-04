plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.devtools.ksp)
}

android {
    namespace = "com.lianyu.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lianyu.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.9.1"

        manifestPlaceholders["developerName"] = "苏苏"
        manifestPlaceholders["developerOrg"] = "LianYu"

        // Developer: 苏苏 / Organization: LianYu

        // Force multi-DEX output
        multiDexEnabled = true

        manifestPlaceholders[
            "VIVO_PUSH_API_KEY"] = project.findProperty("VIVO_PUSH_API_KEY")?.toString() ?: ""
        manifestPlaceholders[
            "VIVO_PUSH_APP_ID"] = project.findProperty("VIVO_PUSH_APP_ID")?.toString() ?: ""

        buildConfigField("String", "HARDENING_LEVEL", "\"OPEN_SOURCE\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    ndkVersion = "30.0.14904198"

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = System.getenv("LIANYU_STORE_PASSWORD") ?: project.findProperty("LIANYU_STORE_PASSWORD") as String? ?: "debug_password_placeholder"
            keyAlias = System.getenv("LIANYU_KEY_ALIAS") ?: project.findProperty("LIANYU_KEY_ALIAS") as String? ?: "your_alias"
            keyPassword = System.getenv("LIANYU_KEY_PASSWORD") ?: project.findProperty("LIANYU_KEY_PASSWORD") as String? ?: "debug_password_placeholder"
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = false  // Shell loads DEX from assets — must not strip
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "LianYu")
        }
    }
    sourceSets {
        getByName("debug") {
            manifest.srcFile("src/shell/AndroidManifest.xml")
        }
        getByName("release") {
            manifest.srcFile("src/shell/AndroidManifest.xml")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // jniLibs are picked up automatically from src/main/jniLibs/
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val pythonExecutable = if (isWindows) "python" else "python3"

val shellPayloadAssetsDir = layout.projectDirectory.dir("src/main/assets/lianyu_shell")
val unsignedReleaseApk = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk")

tasks.register<Exec>("packageShellPayload") {
    group = "security"
    description = "Encrypt release classes*.dex into in-repo one-piece shell payload assets. Requires LIANYU_SHELL_PAYLOAD_KEY for CI smoke packaging; production should use native KMS-compatible exporter."
    dependsOn("assembleRelease")
    onlyIf { providers.environmentVariable("LIANYU_SHELL_PAYLOAD_KEY").orNull != null }
    inputs.file(unsignedReleaseApk)
    outputs.dir(shellPayloadAssetsDir)
    commandLine(
        pythonExecutable,
        "${rootProject.projectDir}/tools/package_shell_payload.py",
        "--apk",
        unsignedReleaseApk.get().asFile.absolutePath,
        "--out",
        shellPayloadAssetsDir.asFile.absolutePath
    )
}


// FIX 1: Strip plaintext classes*.dex from release APK
//
// The encrypted shell payload (assets/lianyu_shell/shell_payload.bin
// and classes.bin) contains the full DEX. The plaintext classes.dex
// in the APK root is a reverse-engineering weakness — it must be
// removed after packaging and before signing.
// ══════════════════════════════════════════════════════════════






dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:ui-common"))

    // Feature modules
    implementation(project(":feature:companion"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:groupchat"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:localmodel"))
    implementation(project(":feature:wechat"))
    implementation(project(":feature:qqbot"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:coffee"))

    // sherpa-onnx: 离线流式语音识别，运行时由 app 模块提供
    implementation(files("../core/network/libs/sherpa-onnx-1.13.3.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.animation.graphics)
    implementation(libs.lottie.compose)
    implementation(libs.androidx.app.update.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.tracing)

    // 厂商 Push SDK
    // OPPO / vivo 使用本地 aar，请从各厂商开放平台下载后放置到 app/libs
    val oppoAar = file("libs/oppo-push-3.0.0.aar")
    if (oppoAar.exists()) {
        implementation(files(oppoAar))
    }
    val vivoAar = file("libs/vivo-push-4.1.5.0.aar")
    if (vivoAar.exists()) {
        implementation(files(vivoAar))
    }
    // 华为 HMS Push 使用 Maven 依赖
    implementation(libs.huawei.hms.push)

    // 小米推送：请从 https://dev.mi.com/ 下载 aar 放到 app/libs/xiaomi-push-x.x.x.aar，
    // 然后取消下面注释并同步 Gradle。
    // implementation(files("libs/xiaomi-push-6.0.1.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}