plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lianyu.ai.security"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
