plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.devtools.ksp)
}

android {
    namespace = "com.lianyu.ai.database"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 导出 Room Schema JSON，用于 CI 检测 Entity 变化导致的 Schema 不兼容
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:security"))
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.security.crypto)
    // SQLCipher: zetetic not in repos
    // TODO: Add SQLCipher repo
    testImplementation(libs.junit)
}

// Room SQLite verifier needs a temp dir with executable permissions.
val sqliteTmpDir = layout.buildDirectory.dir("sqlite-tmp").get().asFile

fun configureRoomSqliteTempDir() {
    sqliteTmpDir.mkdirs()
    System.setProperty("org.sqlite.tmpdir", sqliteTmpDir.absolutePath)
}

configureRoomSqliteTempDir()

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    doFirst {
        configureRoomSqliteTempDir()
    }
}

// Workaround for KSP incremental cache corruption
tasks.withType<Delete>().configureEach {
    delete(layout.buildDirectory.dir("kspCaches"))
}

