import java.util.Properties // Import for java.util.Properties
import java.io.FileInputStream // Import for java.io.FileInputStream

// Your existing code for reading keystore.properties starts here:
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties() // Now Properties() can be resolved
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { fis -> // Use FileInputStream and .use for safety
        keystoreProperties.load(fis)
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boolkafifteen"
    compileSdk = 34 // Or your target SDK

    defaultConfig {
        applicationId = "com.boolkafifteen"
        minSdk = 24
        targetSdk = 34 // Or your target SDK
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists() &&
                keystoreProperties.getProperty("storeFile") != null &&
                keystoreProperties.getProperty("storePassword") != null &&
                keystoreProperties.getProperty("keyAlias") != null &&
                keystoreProperties.getProperty("keyPassword") != null) {

                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                println("WARNING: Release signing keystore.properties not found or incomplete. Release build will not be signed with release key.")
                // For a CI environment or if you want to allow unsigned release builds (not for Play Store)
                // you might have a fallback here, but for Play Store, signing is mandatory.
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug specific configurations if any
            // By default, debug builds are signed with a debug key
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // Or VERSION_11, VERSION_17
        targetCompatibility = JavaVersion.VERSION_1_8 // Or VERSION_11, VERSION_17
    }
    kotlinOptions {
        jvmTarget = "1.8" // Or "11", "17" - should match compileOptions
    }
    buildFeatures {
        // viewBinding = true // If you were using ViewBinding
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0") // Example version
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // Image Cropper Library
    implementation("com.vanniktech:android-image-cropper:4.5.0") // Or your chosen version

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}