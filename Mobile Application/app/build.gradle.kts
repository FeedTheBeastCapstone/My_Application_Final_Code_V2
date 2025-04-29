plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.firebase.messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)              // Firebase Authentication
    implementation(libs.appcompat)                  // AndroidX AppCompat
    implementation(libs.lifecycle.viewmodel.ktx)    // ViewModel with Kotlin extensions
    implementation(libs.material)                   // Material Components
    implementation(libs.activity)                   // AndroidX Activity
    implementation(libs.constraintlayout)           // ConstraintLayout
    implementation(libs.cardview)                   // CardView
    implementation(libs.room.runtime)               // Room Runtime
    implementation(libs.room.ktx)                   // Room KTX for Kotlin extensions
    implementation(libs.recyclerView)               // RecyclerView
    implementation(libs.core.ktx)                   // AndroidX Core KTX
    implementation(libs.firebase.crashlytics)       // Firebase Crashlytics
    implementation(libs.firebase.database)          // Firebase Realtime Database
    implementation(libs.work)                        // WorkManager for background tasks
    kapt(libs.room.compiler)                        // Room Compiler
    testImplementation(libs.junit)                   // JUnit for testing
    androidTestImplementation(libs.ext.junit)       // Ext JUnit for Android testing
    androidTestImplementation(libs.espresso.core)    // Espresso for UI testing
}
