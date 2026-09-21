    plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.timerecording"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.timerecording"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false          // 代码压缩与混淆：当前关闭
            isShrinkResources = false        // 无用资源移除：当前关闭
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    //implementation("com.github.PhilJay:MPAndroidChart:3.1.0")
    implementation("com.github.QuadFlask:colorpicker:0.0.15")
    // Fragment + BottomNavigationView
    implementation("androidx.fragment:fragment-ktx:1.6.2")

// 图片裁剪库（Android-Image-Cropper，简单易用）
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.github.yalantis:ucrop:2.2.11")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Lifecycle ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}