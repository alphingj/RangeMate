# Add project specific ProGuard rules here.

# Keep Hilt components
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Bluetooth classes
-keep class android.bluetooth.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }
