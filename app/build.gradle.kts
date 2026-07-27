plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Applied only once app/google-services.json exists (downloaded from the Firebase console),
// so CI keeps building green before that file is added to the project.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Every CI build gets a higher version than the last, so the installed app can tell that the
// latest GitHub release is newer and the in-app updater has something to offer. Local builds fall
// back to 1 (Android rejects versionCode 0).
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.wishlist.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wishlist.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        // Matches the v1.0.N release tag so UpdateChecker's semver comparison lines up.
        versionName = "1.0.$buildNumber"

        // Owner/repo of the GitHub Releases feed used by the in-app update checker.
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"yuchoi-bb\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"Wishlist\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            // Checked-in on purpose: debug keys aren't sensitive, and a stable one keeps the
            // debug APK's signing fingerprint (and thus the SHA-1 registered for Google Sign-In)
            // the same across every CI build instead of a fresh, different one each run.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // google-auth-library-{oauth2-http,credentials} both ship this, colliding at merge time.
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

// Something elsewhere in the graph pulls grpc-api/grpc-context up to 1.66.0 while grpc-core,
// grpc-android, grpc-okhttp etc. (via firebase-firestore) stay on 1.62.2, which Firestore was
// actually built and tested against. The resulting version-skewed combo crashes at runtime with
// NoClassDefFoundError: io.grpc.InternalGlobalInterceptors. Force every grpc-* artifact back to
// the one Firestore expects so they're all consistent again.
configurations.all {
    resolutionStrategy {
        force(
            "io.grpc:grpc-android:1.62.2",
            "io.grpc:grpc-api:1.62.2",
            "io.grpc:grpc-context:1.62.2",
            "io.grpc:grpc-core:1.62.2",
            "io.grpc:grpc-okhttp:1.62.2",
            "io.grpc:grpc-protobuf-lite:1.62.2",
            "io.grpc:grpc-stub:1.62.2",
            "io.grpc:grpc-util:1.62.2",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firestore (real-time multi-device sync) + Firebase Auth (scopes each user to their own data).
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google sign-in + Drive REST API (appDataFolder manual backup/restore).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
