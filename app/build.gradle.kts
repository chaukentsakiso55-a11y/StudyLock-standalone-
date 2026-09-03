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

android {
    namespace = "com.cyberpulse.studylock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.studylock.student"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.3-uninstall-focus-music"

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
    }

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

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
}
