// Mercato Android app.
//
// Generated inputs come from the repo's shared pipeline and are NOT committed
// (see apps/android/README.md). Before the first build, from the repo root:
//   ./scripts/build-native.sh android      UniFFI Kotlin bindings + JNI .so
//   node scripts/gen-design-tokens.mjs     build/tokens/DesignTokens.kt
//   node scripts/gen-strings.mjs           build/strings/android res tree
// The source sets below point straight at those build/ outputs.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repoRoot = rootProject.projectDir.resolve("../..")

// CSV dataset and fonts ship inside the APK as assets. They are staged into
// the build dir so the assets root only contains what the app really uses.
val stageAssets = tasks.register<Copy>("stageAssets") {
    from(repoRoot.resolve("data")) {
        include("*.csv")
        into("data")
    }
    from(repoRoot.resolve("design/fonts")) {
        include("*.ttf")
        into("fonts")
    }
    into(layout.buildDirectory.dir("stagedAssets"))
}

android {
    namespace = "com.mercato.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nicogaray.mercato"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Google's published demo IDs: development never generates invalid
        // traffic. See https://developers.google.com/admob/android/test-ads
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_SPONSOR_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_RECTANGLE_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        }
        // Production ad units of the "Mercato" Android app entry in the
        // AdMob console (account ca-app-pub-5435447054359850).
        release {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = "ca-app-pub-5435447054359850~6149652518"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-5435447054359850/8524534412\"")
            buildConfigField("String", "ADMOB_SPONSOR_ID", "\"ca-app-pub-5435447054359850/3959557736\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-5435447054359850/2430165280\"")
            buildConfigField("String", "ADMOB_RECTANGLE_ID", "\"ca-app-pub-5435447054359850/5958080825\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main") {
        kotlin.srcDir(repoRoot.resolve("build/bindings/kotlin"))
        kotlin.srcDir(repoRoot.resolve("build/tokens"))
        res.srcDir(repoRoot.resolve("build/strings/android"))
        jniLibs.srcDir(repoRoot.resolve("build/android/jniLibs"))
        assets.srcDir(layout.buildDirectory.dir("stagedAssets"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild") {
    dependsOn(stageAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Required by the generated UniFFI bindings.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Google Mobile Ads (AdMob).
    implementation("com.google.android.gms:play-services-ads:23.3.0")
}
