plugins {
    id("com.android.application")
}

android {
    namespace = "com.nfrdev.blockblitzhost"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nfrdev.blockblitzhost"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
    implementation("androidx.compose.runtime:runtime:1.10.0")
    implementation("androidx.compose.ui:ui-text:1.10.0")

    testImplementation("junit:junit:4.13.2")
}
