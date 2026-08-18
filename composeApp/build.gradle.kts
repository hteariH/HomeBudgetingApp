import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing config. Generate the keystore and the properties file once
// (see docs/RELEASE_CHECKLIST.md) — neither is committed to git.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile").orEmpty().isNotBlank()

// AdMob production IDs live in the same keystore.properties file. The fallbacks below are
// the Google test IDs from https://developers.google.com/admob/android/test-ads — safe to
// ship to internal testers, never to production.
val admobAppId = keystoreProps.getProperty("admobAppId")
    ?: "ca-app-pub-3940256099942544~3347511713"
val admobBanner = keystoreProps.getProperty("admobBannerUnitId")
    ?: "ca-app-pub-3940256099942544/6300978111"
val admobInterstitial = keystoreProps.getProperty("admobInterstitialUnitId")
    ?: "ca-app-pub-3940256099942544/1033173712"
val privacyPolicyUrl = keystoreProps.getProperty("privacyPolicyUrl")
    ?: "https://gist.github.com/hteariH/bfd7d6412cc41a227bcc53646655bfc2"

// Backend the app syncs with. 10.0.2.2 is the emulator's route to the host machine,
// so a debug build talks to a server running locally; release points at production.
// Override either by putting apiBaseUrl / apiBaseUrlDebug in keystore.properties.
val apiBaseUrl = keystoreProps.getProperty("apiBaseUrl") ?: "https://homebudget.example.com/"
val apiBaseUrlDebug = keystoreProps.getProperty("apiBaseUrlDebug") ?: "http://10.0.2.2:8085/"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.google.material)
            implementation(libs.androidx.splashscreen)
            implementation(libs.androidx.biometric)
            // EncryptedSharedPreferences for the auth tokens - the app had no
            // encrypted storage at all, and a refresh token is a 60-day credential.
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.activity.compose)

            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.graphics)
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.material.icons.extended)

            implementation(libs.androidx.navigation.compose)

            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)

            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.work.runtime.ktx)

            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)

            implementation(libs.koin.core)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.koin.androidx.workmanager)

            implementation(libs.coil.compose)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.android)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.play.services.ads)
            implementation(libs.user.messaging.platform)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.androidx.room.testing)
            implementation(libs.koin.test)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.espresso.core)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.mockk.android)
        }
    }
}

android {
    namespace = "com.personalmoney"
    // 36 because androidx.activity 1.12+ (pulled in by Koin) refuses to compile against 35.
    // targetSdk stays at 35 deliberately: that one opts the app into new runtime behaviour
    // and is a release decision, not a dependency-migration one.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.personalmoney"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        resourceConfigurations += listOf("en", "ru", "uk")

        manifestPlaceholders["admobAppId"] = admobAppId
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBanner\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitial\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    // Exported Room schemas double as fixtures for MigrationTestHelper, which reads
    // them from the androidTest assets.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// AppDatabase sets exportSchema = true, but without this the schemas were never
// written anywhere - so there was no baseline to test a migration against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Variant-scoped and annotation-processor entries stay in the AGP block: the KMP
    // source sets above have no notion of a debug-only dependency, and KSP is wired
    // per-target (kspAndroid) rather than once for the module.
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("kspAndroid", libs.androidx.room.compiler)
}
