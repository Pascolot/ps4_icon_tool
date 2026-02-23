plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    
    namespace = "com.sml.ps4icontool"
    compileSdk = 36 
    
    defaultConfig {
        applicationId = "com.sml.ps4icontool"
        minSdk = 24
        targetSdk = 36  
        versionCode = 1
        versionName = "1.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // --- SIGNING BLOCK ADDED ---
    signingConfigs {
    create("release") {
        storeFile = file("kizeokey.jks")  // ✅ relative to app/ folder
        storePassword = "mixiaomi16"
        keyAlias = "hfw"
        keyPassword = "mixiaomi16"
        enableV1Signing = true
        enableV2Signing = true
        }
    }

    buildTypes {
        release {
            // We link the signing config we created above
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // ou VERSION_21
        targetCompatibility = JavaVersion.VERSION_17  // ou VERSION_21
    }

    kotlin  {
        jvmToolchain(17)  // ou 21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("commons-net:commons-net:3.10.0") // For FTP
    implementation("io.coil-kt:coil-compose:2.6.0")  // For displaying images
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
     // ⚠️ AJOUTEZ CETTE LIGNE SI ELLE MANQUE :
    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}