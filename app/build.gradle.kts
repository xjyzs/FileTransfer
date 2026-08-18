plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "com.xjyzs.filetransfer"

    compileSdk = 37

    signingConfigs {
        val hasSigningInfo = System.getenv("KEY_STORE_PASSWORD") != null &&
                System.getenv("KEY_ALIAS") != null &&
                System.getenv("KEY_PASSWORD") != null &&
                file("${project.rootDir}/keystore.jks").exists()
        if (hasSigningInfo) {
            create("release") {
                storeFile = file("${project.rootDir}/keystore.jks")
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                enableV1Signing = false
            }
        }
    }

    defaultConfig {
        applicationId = "com.xjyzs.filetransfer"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "abi"

    productFlavors {
        val signingConfig = if (signingConfigs.findByName("release") != null) {
            signingConfigs.getByName("release")
        } else {
            signingConfigs.getByName("debug")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters.add("x86_64") }
            this.signingConfig = signingConfig
        }
        create("arm64Minsdk35") {
            dimension = "abi"
            ndk { abiFilters.add("arm64-v8a") }
            minSdk = 35
            this.signingConfig = signingConfig
        }
        create("arm64Minsdk29") {
            dimension = "abi"
            ndk { abiFilters.add("arm64-v8a") }
            minSdk = 29
            this.signingConfig = signingConfig
        }
        create("arm64Minsdk26") {
            dimension = "abi"
            ndk { abiFilters.add("arm64-v8a") }
            minSdk = 26
            this.signingConfig = signingConfig
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("x86_64")
            }
            this.signingConfig = signingConfig
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
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/**",
                "kotlin/**"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
    }
}


tasks.configureEach {
    doLast {
        outputs.files.forEach { outputDir ->
            val filesToDelete = setOf("PublicSuffixDatabase.list")
            for (i in filesToDelete) {
                val file = outputDir.resolve(i)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}

configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
    exclude(group = "androidx.navigation", module = "navigationevent-compose")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.ui.graphics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.material.icons.extended)
    implementation(libs.core) // 二维码

    implementation(libs.miuix.navigation)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}