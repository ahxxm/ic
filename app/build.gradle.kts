plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.ahxxm.ic"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("../${System.getenv("KEYSTORE_FILE") ?: "release.keystore"}")
            storePassword = System.getenv("STORE_PASSWORD") ?: "yourpassword"
            keyAlias = "release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "yourpassword"
        }
    }

    val baseVersion = 5  // Must match git tag: v$baseVersion.0

    defaultConfig {
        applicationId = "io.ahxxm.ic"
        minSdk = 30
        targetSdk = 36
        versionCode = baseVersion
        versionName = "$baseVersion.0"
        vectorDrawables.generatedDensities?.clear()
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val baseVersionCode = output.versionCode.get()
            output.versionCode.set(baseVersionCode * 10 + (abiCodes[abiFilter] ?: 0))
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.serialization.json)
    implementation(libs.core.ktx)
    implementation(libs.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Native image encoders
    implementation(libs.aire)
    implementation(libs.jpegli.coder)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
}
