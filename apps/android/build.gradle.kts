plugins {
    // GMA SDK 25 requires compileSdk 35; its artifacts carry Kotlin 2.3
    // metadata, which needs a Kotlin 2.2+ compiler (N+1 metadata rule).
    // AGP 8.7 pairs with the Gradle 8.9 wrapper and supports compileSdk 35.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}
