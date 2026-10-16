// Modified for Cozy Flooring: distinct application ID.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.remmeasure"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.cozyflooring.measure"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseStore = providers.environmentVariable("COZY_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseStore != null) create("cozyRelease") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("COZY_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("COZY_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("COZY_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        debug { versionNameSuffix = "-test" }
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("cozyRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // AR & 3D Rendering (Sceneview + ARCore)
    implementation(libs.sceneview.ar)
    implementation(libs.arcore)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release", ignoreCase = false) }) {
        listOf("COZY_KEYSTORE_PATH", "COZY_STORE_PASSWORD", "COZY_KEY_ALIAS", "COZY_KEY_PASSWORD").forEach {
            require(!System.getenv(it).isNullOrBlank()) { "Signed releases require $it. See RELEASE.md." }
        }
    }
}
