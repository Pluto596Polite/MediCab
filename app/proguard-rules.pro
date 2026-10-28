
# ML Kit (barcode + text recognition) wires its internals up by reflection, which
# R8 can't see. Without these rules the shrunk app crashed closing the scanner.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-dontwarn com.google.mlkit.**
