plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.ahxxm.ic"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "yourpassword"
            keyAlias = "release"
            keyPassword = "yourpassword"
        }
    }

    defaultConfig {
        applicationId = "io.ahxxm.ic"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "META-INF/**/*.version"
            excludes += "META-INF/**/LICENSE*.txt"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Native image encoders
    implementation(libs.aire)
    implementation(libs.jpegli.coder)

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling)
}
