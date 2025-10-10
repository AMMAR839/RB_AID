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

    // Model .tflite jangan di-compress
    androidResources { noCompress += "tflite" }

    sourceSets {
        getByName("main") {
            assets {
                // pakai path yang kamu gunakan
                srcDirs("src\\main\\assets", "src\\main\\assets\\models")
            }
        }
    }
}

dependencies {
    // ---------- AndroidX dasar ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.firebase:firebase-firestore:24.10.0")
    implementation("com.google.firebase:firebase-storage:20.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.pytorch:pytorch_android:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision:1.13.1")

    // Material & Splashscreen
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)

    // ---------- CameraX ----------
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
//    pythorc
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.firebase:firebase-firestore:24.10.0")
    implementation("com.google.firebase:firebase-storage:20.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.pytorch:pytorch_android:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision:1.13.1")

    implementation("androidx.exifinterface:exifinterface:<latest>")
    // ---------- Coroutines & Lifecycle ----------
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ---------- Firebase (pakai BOM) ----------
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.ml.modeldownloader)

    // Firestore & Realtime Database (NON-KTX, sesuai kebutuhanmu)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)

    // ---------- Google Sign-In & Location ----------
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    // Tidak perlu play-services-base (transitif)

    // ---------- Credentials / Google Identity (opsional) ----------
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ---------- Test ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
