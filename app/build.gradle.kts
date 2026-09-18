plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.xmarcade"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xmarcade"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Signing — uses env ORG_GRADLE_PROJECT_XM_* or local xm-arcade-release-secrets/passwords.sh
    // Original agent generated: xm-arcade-release.keystore (RSA-4096, valid to 2056)
    val xmStoreFile = (findProperty("XM_STORE_FILE") as String? ?: System.getenv("XM_STORE_FILE") ?: System.getenv("ORG_GRADLE_PROJECT_XM_STORE_FILE"))
        ?.let { path -> File(path) }?.takeIf { f -> f.exists() }
    val xmStorePassword = findProperty("XM_STORE_PASSWORD") as String? ?: System.getenv("XM_STORE_PASSWORD") ?: System.getenv("ORG_GRADLE_PROJECT_XM_STORE_PASSWORD")
    val xmKeyAlias = findProperty("XM_KEY_ALIAS") as String? ?: System.getenv("XM_KEY_ALIAS") ?: System.getenv("ORG_GRADLE_PROJECT_XM_KEY_ALIAS") ?: "xmarcade"
    val xmKeyPassword = findProperty("XM_KEY_PASSWORD") as String? ?: System.getenv("XM_KEY_PASSWORD") ?: System.getenv("ORG_GRADLE_PROJECT_XM_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (xmStoreFile != null && xmStorePassword != null && xmKeyPassword != null) {
                storeFile = xmStoreFile
                storePassword = xmStorePassword
                keyAlias = xmKeyAlias
                keyPassword = xmKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.activity.compose)
    implementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.runtime)
    ksp("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")
    implementation(libs.androidx.room.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.secp256k1)
    implementation(libs.bip32)

    // Accompanist pager for vertical shorts (horizontalPager is now in compose.foundation)
    implementation("com.google.accompanist:accompanist-pager:0.34.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    implementation("androidx.compose.foundation:foundation")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
