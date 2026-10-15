import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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

val privacyPolicyUrl = keystoreProps.getProperty("privacyPolicyUrl")
    ?: "https://gist.github.com/hteariH/bfd7d6412cc41a227bcc53646655bfc2"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS targets are deliberately absent: Kotlin/Native cannot build Apple targets on a
    // Windows host, so declaring them here would only add tasks that can never run. They
    // go in when the build moves to a Mac. Shared code is written to iOS constraints
    // regardless - no JVM types in commonMain - so adding them stays a one-liner.
    //
    // There was a wasmJs target too, serving the web app; the web client is React now
    // (src/main/frontend in the server repository root), so Android is the only target.

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform, not the androidx artifacts, so commonMain stays ready for
            // another target. On Android these resolve to the same androidx artifacts.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // IconLibrary maps ~29 icons by name. The set is large, but R8 keeps only the
            // icons actually referenced.
            implementation(compose.materialIconsExtended)
            // Multiplatform strings. Every user-facing string lives in composeResources
            // rather than res/values - see the note on the compose.resources block below.
            implementation(compose.components.resources)
            // ViewModel, navigation and Koin's Compose bindings, in their multiplatform
            // form. These are what let a feature package live in commonMain: the same
            // `viewModel()` / `NavHost` / `koinViewModel()` calls compile for any target,
            // rather than being androidx dependencies only Android can see.
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // Multiplatform BigDecimal. Money is Long minor units, so this is only needed
            // for FX multiplication and for parsing decimal strings off the wire - the
            // server sends every BigDecimal as a string.
            implementation(libs.bignum)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // Lets the error-handling tests drive a real HttpClient: the thing under test
            // is how Ktor's own exceptions carry a server body, which a hand-rolled fake
            // would only assert about itself.
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

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

            implementation(libs.tesseract4android)
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
            // Spelled out rather than inherited: AGP does not put the tested variant's
            // `implementation` dependencies on the instrumented-test *compile* classpath,
            // so ReceiptRecognitionTest cannot see ML Kit, coroutines or LocalDate without
            // asking for them here.
            implementation(libs.tesseract4android)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // MigrationTest has always needed this and never had it, so the whole
            // instrumented source set failed to compile - invisible because the build gate
            // is testDebugUnitTest + assembleDebug, neither of which touches it.
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.espresso.core)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.mockk.android)
        }
    }
}

// Strings, and later any other shared asset, are generated into one predictable class
// rather than the per-module default `<module>.generated.resources`, so `Res.string.x`
// imports the same way from every source set.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.personalmoney.resources"
    generateResClass = always
}

android {
    namespace = "com.personalmoney"
    // 36 because androidx.activity 1.12+ (pulled in by Koin) refuses to compile against 35.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.personalmoney"
        minSdk = 26
        // 36 because Play stopped accepting anything lower on 31 August 2026 - a new app
        // targeting 35 is rejected at upload, so this is no longer a free choice.
        //
        // The Android 16 behaviour it opts into costs this app nothing: MainActivity
        // already calls enableEdgeToEdge(), the manifest already sets
        // enableOnBackInvokedCallback and pins no screenOrientation, and none of the
        // health, Bluetooth or local-network changes touch anything here. What is left is
        // insets - verify the header, the bottom bar and the Quick Add sheet on a device
        // before a release goes out.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        resourceConfigurations += listOf("en", "ru", "uk")
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
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
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
