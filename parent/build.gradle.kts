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
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val clientConfig = configValue("STUDYLOCK_CONFIG_A")

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

        buildConfigField("String", "FIREBASE_API_KEY", clientConfig.asBuildConfigString())
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
