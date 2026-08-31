import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
}

android {
    namespace = "com.nfrdev.blockblitzhost"
    compileSdk = 37

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.nfrdev.blockblitzhost"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")

    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
