plugins {
    // GMA SDK 25 requires compileSdk 35 or newer; its artifacts carry Kotlin 2.3
    // metadata, which needs a Kotlin 2.2+ compiler (N+1 metadata rule).
    // AGP 8.9 pairs with the Gradle 8.11 wrapper and is the first line that
    // supports compileSdk 36, which Play requires for new apps from 31 Aug 2026.
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    // Analytics and Crashlytics. Declared here but applied conditionally in
    // app/build.gradle.kts: the google-services plugin fails the build when
    // google-services.json is absent, and a clone without Firebase
    // credentials still has to compile.
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
