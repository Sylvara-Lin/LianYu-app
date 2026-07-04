plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    // core:domain 核心只依赖 kotlinx-coroutines（语言级基础设施），无其他业务依赖
    implementation(libs.kotlinx.coroutines.core)
}

android {
    namespace = "com.lianyu.ai.domain"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
