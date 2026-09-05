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
val prepareStudyLockPrivateAiKey by tasks.registering {
    val outputFile = generatedPrivateAiAssetsDir.map { it.file("studylock-private-ai-key.txt") }
    val parentConfigFile = generatedPrivateAiAssetsDir.map { it.file("studylock-firebase-parent-config.js") }
    outputs.file(outputFile)
    outputs.file(parentConfigFile)
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

        val apiKey = configValue(
            "STUDYLOCK_FIREBASE_API_KEY",
            "AIzaSyAicvQXGfV2o2mV1zjO3PNe98lrj9DPojc"
        ).asBuildConfigString()
        val projectId = configValue("STUDYLOCK_FIREBASE_PROJECT_ID", "studylock-family").asBuildConfigString()
        val storageBucket = configValue(
            "STUDYLOCK_FIREBASE_STORAGE_BUCKET",
            "studylock-family.firebasestorage.app"
        ).asBuildConfigString()
        val configDestination = parentConfigFile.get().asFile
        configDestination.parentFile.mkdirs()
        configDestination.writeText(
            "window.__STUDYLOCK_FIREBASE_PARENT_CONFIG={" +
                "apiKey:$apiKey," +
                "authDomain:\"studylock-family.firebaseapp.com\"," +
                "projectId:$projectId," +
                "storageBucket:$storageBucket" +
            "};"
        )
    }
}

val generatedLauncherResDir = layout.buildDirectory.dir("generated/studylockLauncherRes")
val launcherIconSource = rootProject.file("app/icon/studylock_icon_proper.webp.b64")
val launcherIconFile = generatedLauncherResDir.get()
    .file("drawable-nodpi/studylock_icon_proper.webp")
    .asFile
launcherIconFile.parentFile.mkdirs()
launcherIconFile.writeBytes(
    Base64.getDecoder().decode(launcherIconSource.readText().trim())
)

android {
    namespace = "com.cyberpulse.studylock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.studylock.student"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.16-reference-websites"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
                "STUDYLOCK_FIREBASE_APP_ID",
                "1:126746983812:android:05e571925837aafa98b1d1"
            ).asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            configValue("STUDYLOCK_FIREBASE_PROJECT_ID", "studylock-family").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_STORAGE_BUCKET",
            configValue(
                "STUDYLOCK_FIREBASE_STORAGE_BUCKET",
                "studylock-family.firebasestorage.app"
            ).asBuildConfigString()
        )
        buildConfigField("int", "DICTIONARY_ASSET_VERSION", "1")
        buildConfigField(
            "String",
            "OFFLINE_LIBRARY_STORAGE_PATH",
            configValue(
                "STUDYLOCK_OFFLINE_LIBRARY_STORAGE_PATH",
                "offline-tutor-library/studylock-reference-library-v1.db"
            ).asBuildConfigString()
        )
        buildConfigField("int", "OFFLINE_LIBRARY_VERSION", "1")
    }

    sourceSets["main"].assets.srcDir(generatedPrivateAiAssetsDir)
    sourceSets["main"].res.srcDir(generatedLauncherResDir)

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.matching {
    it.name.contains("Assets", ignoreCase = true) ||
        it.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareStudyLockPrivateAiKey)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
}
