# UniFFI reaches these from the native library by name, so R8 must not rename
# or remove them. Everything else in the app is fair game.
-keep class uniffi.mercato_ffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# JNA loads its own native helper reflectively.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
