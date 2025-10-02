plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.app_rb_aid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.app_rb_aid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { viewBinding = true }

    androidResources {
        // Kotlin DSL: pakai +=
//        aaptOptions { noCompress("tflite") }
        noCompress += "tflite"
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src\\main\\assets", "src\\main\\assets\\models")
            }
        }
    }
}

dependencies {
    val camerax_version = "1.3.4"

    // --- AndroidX dasar ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Material (pilih satu sumber; pakai versi eksplisit ini) ---
    implementation("com.google.android.material:material:1.12.0")
    // (hapus libs.material agar tidak dobel)

    // --- Splashscreen (stabil) ---
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- CameraX ---
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // --- TensorFlow Lite (JANGAN dobel) ---
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Optional (hapus kalau tidak dipakai):
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // --- Coroutines & Lifecycle ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // --- Firebase (pakai BOM agar versi konsisten) ---
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-ml-modeldownloader")

    // --- Google Sign-In klasik (yang dipakai di LoginActivity) ---
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.gms:play-services-base:18.4.0")

    // --- Test ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // (Dihapus supaya tidak konflik/duplikat)
    // implementation(libs.material)
    // implementation(libs.firebase.auth)
    // implementation("com.google.firebase:firebase-auth:24.0.1")
    // implementation(libs.androidx.credentials)
    // implementation(libs.androidx.credentials.play.services.auth)
    // implementation(libs.googleid)
}
