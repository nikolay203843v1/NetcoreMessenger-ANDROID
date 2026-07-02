plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("com.google.gms.google-services")
}

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val baseUrl: String = localProps.getProperty("BASE_URL") ?: "https://example.invalid"
val wsUrl: String = localProps.getProperty("WS_URL") ?: "wss://example.invalid"
// Тот же бот и тот же канал, что и на бэкенде (см. TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID в .env сервера).
// Нужен приложению только чтобы ЧИТАТЬ последнее сообщение с адресом — на старте, ещё до логина.
val telegramBotToken: String = localProps.getProperty("TELEGRAM_BOT_TOKEN") ?: ""
val telegramChatId: String = localProps.getProperty("TELEGRAM_CHAT_ID") ?: ""

android {
    namespace = "com.netcoremessenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netcoremessenger.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "WS_URL", "\"$wsUrl\"")
        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$telegramBotToken\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"$telegramChatId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (offline-first local DB)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // DataStore (token storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Material Components (XML theme)
    implementation("com.google.android.material:material:1.12.0")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Media3 (ExoPlayer for video/voice)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")

    // CameraX (Telegram-style video circles)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Firebase + Google Auth
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // WebRTC (Stream's fork — поддерживается, ARM64 + x86_64)
    implementation("io.getstream:stream-webrtc-android:1.3.7")

    // Permissions helper
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
