import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.sotuvchi.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.sotuvchi.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Production API root. Endpoint paths are relative to it, so moving to
        // /api/v1 is a one-line change. Web pages use the same host's root.
        buildConfigField("String", "API_URL", "\"https://sotuvchi.ai/api\"")
    }

    // Upload key for Play: put the details in keystore.properties (not committed). Without it the
    // release build stays unsigned. `-PsignWithDebug` signs a release build with the debug key,
    // only to try the shrunk app on an emulator.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds can point at the mock server: see Tools/mock-server
            // and the apiURL override in ApiConfig.
            buildConfigField("boolean", "DEBUG_OVERRIDES", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "DEBUG_OVERRIDES", "false")
            signingConfig = when {
                project.hasProperty("signWithDebug") -> signingConfigs.getByName("debug")
                keystoreFile.exists() -> signingConfigs.getByName("upload")
                else -> null
            }
        }
    }

    // Same shrinking as release, but debug-signed with the API override and the server banner on:
    // `make ui-test-qa` runs the whole UI suite against the minified code with the mock.
    buildTypes.create("qa") {
        initWith(buildTypes.getByName("release"))
        matchingFallbacks += "release"
        signingConfig = signingConfigs.getByName("debug")
        buildConfigField("boolean", "DEBUG_OVERRIDES", "true")
        // -PnoQaKeeps drops the keep rules that let the test APK run, to try the shrinker at full strength by hand.
        if (!providers.gradleProperty("noQaKeeps").isPresent) proguardFile("proguard-qa-rules.pro")
        testProguardFiles("proguard-test-rules.pro")
    }

    // Instrumented tests run against this build type: -PtestBuild=qa for the shrunk build.
    testBuildType = providers.gradleProperty("testBuild").getOrElse("debug")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI tests pull an older Espresso that breaks on Android 17; pin a current one.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
