import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun configValue(name: String, fallback: String = ""): String =
    listOf(
        providers.environmentVariable(name).orNull,
        providers.gradleProperty(name).orNull,
        localProperties.getProperty(name)
    ).firstOrNull { !it.isNullOrBlank() } ?: fallback

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val generatedPrivateAiAssetsDir = layout.buildDirectory.dir("generated/privateAiAssets")
val prepareStudyLockParentPrivateAiKey by tasks.registering {
    val outputFile = generatedPrivateAiAssetsDir.map { it.file("studylock-private-ai-key.txt") }
    outputs.file(outputFile)
    doLast {
        val encrypted = Base64.getDecoder().decode(
            "5fmbObGszd5BWMbYrzNmQtKKNiNCAQoTM+NZLDg56EPKmdoz58vOxSBXhZysBj4hy61QIVY="
        )
        val mask = MessageDigest.getInstance("SHA-256")
            .digest("StudyLock-CyberPulse-Private-AI-v1".toByteArray(Charsets.UTF_8))
        val plain = ByteArray(encrypted.size) { index ->
            (encrypted[index].toInt() xor mask[index % mask.size].toInt()).toByte()
        }
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeBytes(plain)
    }
}

val generatedLauncherResDir = layout.buildDirectory.dir("generated/studylockParentLauncherRes")
val launcherIconSource = rootProject.file("app/icon/studylock_icon_proper.webp.b64")
val launcherIconFile = generatedLauncherResDir.get()
    .file("drawable-nodpi/studylock_icon_proper.webp")
    .asFile
launcherIconFile.parentFile.mkdirs()
launcherIconFile.writeBytes(Base64.getDecoder().decode(launcherIconSource.readText().trim()))

android {
    namespace = "com.cyberpulse.studylock.parent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.studylock.parent"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.1.0-parent"

        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            configValue(
                "STUDYLOCK_FIREBASE_API_KEY",
                "AIzaSyAicvQXGfV2o2mV1zjO3PNe98lrj9DPojc"
            ).asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            configValue(
                "STUDYLOCK_PARENT_FIREBASE_APP_ID",
                "1:126746983812:android:05e571925837aafa98b1d1"
            ).asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            configValue("STUDYLOCK_FIREBASE_PROJECT_ID", "studylock-family").asBuildConfigString()
        )
    }

    sourceSets["main"].assets.srcDir(generatedPrivateAiAssetsDir)
    sourceSets["main"].res.srcDir(generatedLauncherResDir)

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

tasks.matching {
    it.name.contains("Assets", ignoreCase = true) || it.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareStudyLockParentPrivateAiKey)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
