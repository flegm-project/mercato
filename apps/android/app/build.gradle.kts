import java.util.Properties

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

// Firebase is opt-in on the presence of its own credentials. google-services
// fails the build outright when google-services.json is missing, so applying
// it unconditionally would mean nobody can compile the app without a Firebase
// project of their own. `Analytics.kt` reads the same condition at runtime and
// becomes a no-op, so the two never disagree.
val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle(
        "note: apps/android/app/google-services.json is absent, so Firebase " +
            "Analytics and Crashlytics are compiled out of this build."
    )
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
    // Synthesised, not recorded: scripts/gen-sounds.mjs writes these, and
    // genSounds below runs it, so a fresh clone builds with sound.
    from(repoRoot.resolve("build/sounds")) {
        include("*.wav")
        into("sounds")
    }
    into(layout.buildDirectory.dir("stagedAssets"))
}

val genSounds = tasks.register<Exec>("genSounds") {
    workingDir = repoRoot
    commandLine("node", "scripts/gen-sounds.mjs")
    inputs.file(repoRoot.resolve("scripts/gen-sounds.mjs"))
    outputs.dir(repoRoot.resolve("build/sounds"))
}

// The event vocabulary is generated from design/analytics.json for both
// platforms at once, so neither app can invent or misspell an event name.
val genAnalytics = tasks.register<Exec>("genAnalytics") {
    workingDir = repoRoot
    commandLine("node", "scripts/gen-analytics.mjs")
    inputs.file(repoRoot.resolve("scripts/gen-analytics.mjs"))
    inputs.file(repoRoot.resolve("design/analytics.json"))
    outputs.dir(repoRoot.resolve("build/analytics"))
}

// The launcher icon is generated from the design tokens, like the strings and
// the type tokens. It used to be generated into build/icons and then copied by
// hand into src/main/res, which is how the app shipped for months with an icon
// nobody had regenerated: the mark was redrawn and the phone kept the old one.
// Generating it as part of the build is what stops that happening again.
//
// The intro's geometry, generated for both platforms at once so the two
// animations cannot drift into two different pictures of the same game. The
// drawing itself is OnboardingArt.kt.
val genArt = tasks.register<Exec>("genArt") {
    workingDir = repoRoot
    commandLine("node", "scripts/gen-onboarding-scenes.mjs")
    inputs.file(repoRoot.resolve("scripts/gen-onboarding-scenes.mjs"))
    inputs.file(repoRoot.resolve("design/onboarding.json"))
    outputs.dir(repoRoot.resolve("build/art"))
}

val genIcons = tasks.register<Exec>("genIcons") {
    workingDir = repoRoot
    commandLine("node", "scripts/gen-app-icon.mjs")
    inputs.file(repoRoot.resolve("scripts/gen-app-icon.mjs"))
    inputs.file(repoRoot.resolve("design/tokens.json"))
    inputs.file(repoRoot.resolve("design/fonts/Unbounded-Black.ttf"))
    outputs.dir(repoRoot.resolve("build/icons/android/res"))
}

android {
    // Release signing. The keystore and its passwords never enter the repo:
    // they come from keystore.properties (git-ignored) or, in CI, from the
    // environment. Without either, the release build is left unsigned rather
    // than failing, so a debug-only checkout still builds.
    val keystoreProperties = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun secret(key: String, env: String): String? =
        keystoreProperties.getProperty(key) ?: System.getenv(env)


    namespace = "com.mercato.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flegm.mercato"
        minSdk = 26
        targetSdk = 36
        // 2 is spent: Play took that bundle and then refused it over the
        // 16 KB alignment, and a version code cannot be uploaded twice.
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Ship only the ABIs the Rust core is built for. JNA otherwise
            // drags in mips, x86 and armeabi copies of its helper, on which
            // libmercato_ffi does not exist and the app could not run anyway.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        val storePath = secret("storeFile", "MERCATO_KEYSTORE")
        if (storePath != null && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = secret("storePassword", "MERCATO_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "MERCATO_KEY_ALIAS")
                keyPassword = secret("keyPassword", "MERCATO_KEY_PASSWORD")
            }
        }
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
            signingConfig = signingConfigs.findByName("release")
            // R8 shrinks and obfuscates. The rules keep the JNI entry points
            // and the UniFFI types the native library reflects on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        kotlin.srcDir(repoRoot.resolve("build/analytics"))
        // The intro's geometry, shared with iOS. Drawn by OnboardingArt.kt.
        kotlin.srcDir(repoRoot.resolve("build/art"))
        res.srcDir(repoRoot.resolve("build/strings/android"))
        res.srcDir(repoRoot.resolve("build/icons/android/res"))
        // The launch theme's colour, generated from the tokens: XML cannot read
        // the Kotlin token object, and the window is painted before any of this
        // app's code runs.
        res.srcDir(repoRoot.resolve("build/tokens/android"))
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
    dependsOn(stageAssets, genIcons, genAnalytics, genArt)
}
stageAssets { dependsOn(genSounds) }

dependencies {
    // play-services-basement, which play-services-ads pulls in, still asks for
    // androidx.fragment 1.1.0, and AndroidX marked that version obsolete: Play
    // reports it against every upload even though no screen here is a Fragment.
    // A constraint raises the version resolution settles on without declaring a
    // dependency the app does not have.
    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("1.1.0, via play-services-basement, is flagged obsolete by Play")
        }
    }

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
    //
    // The version matters for a reason that has nothing to do with UniFFI.
    // 5.14.0 ships an x86_64 libjnidispatch.so linked for 4 KB memory pages,
    // and Play rejects any 64-bit library that cannot be mapped on a 16 KB
    // page device: that is what "this release contains new app bundles that
    // are not compatible with 16 KB memory page sizes" meant on version code
    // 2. 5.17.0 is the first release linked with -Wl,-z,max-page-size=16384
    // (java-native-access/jna#1654). verify16kAlignment is what stops the
    // next bump quietly undoing it.
    implementation("net.java.dev.jna:jna:5.19.1@aar")

    // Google Mobile Ads (AdMob) + the UMP consent SDK it pins.
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // Play Billing: the single remove-ads non-consumable.
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Analytics and Crashlytics. Both are no-cost on the Spark plan with no
    // usage limits; nothing else from Firebase is used, and nothing else is
    // wanted, since the rest of it is a backend this app does not have.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation("junit:junit:4.13.2")

    // Instrumented UI tests. These assert on geometry, not on screenshots: a
    // screenshot test tells you something moved without saying what, and gets
    // re-baselined the moment it is inconvenient. "The title starts at least
    // 12dp after the arrow" survives a font change and still fails when the
    // gap goes.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
}

// Every 64-bit .so in the upload has to be mapped on a 16 KB page device.
// Gradle knows nothing about this, and neither does lint: the alignment lives
// in the ELF program headers of libraries that mostly arrive prebuilt from
// dependencies. Reading it out of the finished artifact is the only check that
// sees what Play sees, so it runs on the artifact, after it is assembled.
//
// One task per artifact, rather than one task reading both. bundleRelease does
// not touch the APK, so a single task would have judged an app-release.apk
// left over from an earlier build and failed a bundle that was in fact
// correct: the check would have been reporting on a file nothing had just
// produced.
fun register16kCheck(name: String, artifact: String) =
    tasks.register<Exec>(name) {
        description = "Fails if a packaged 64-bit .so is not 16 KB page aligned."
        workingDir = repoRoot
        commandLine(
            "python3", "scripts/check-16k.py",
            layout.buildDirectory.file(artifact).get().asFile.path,
        )
    }

val verifyBundle16k =
    register16kCheck("verify16kAlignmentBundle", "outputs/bundle/release/app-release.aab")
val verifyApk16k =
    register16kCheck("verify16kAlignmentApk", "outputs/apk/release/app-release.apk")

tasks.matching { it.name == "bundleRelease" }.configureEach { finalizedBy(verifyBundle16k) }
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(verifyApk16k) }

// Pin the JDK the build runs on. Gradle 8.9 rejects anything newer than 22,
// and a machine whose only JDK is newer fails with a bare version number that
// says nothing about the cause.
kotlin {
    jvmToolchain(21)
}
