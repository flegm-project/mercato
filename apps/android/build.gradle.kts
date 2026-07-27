plugins {
    // GMA SDK 25 requires compileSdk 35 and Kotlin >= 2.1.0; AGP 8.7 pairs
    // with the Gradle 8.9 wrapper and supports compileSdk 35.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
