# AniKitty release shrink rules.
# Libraries (Media3, Room, Coil, OkHttp, Cast, Tink) ship their own consumer rules.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep class site.anikitty.app.AnikittyApp { *; }
-keep class site.anikitty.app.MainActivity { *; }

# Room entities and DAOs are touched from generated code and from the process.
-keep class site.anikitty.app.data.local.** { *; }
-keep class site.anikitty.app.data.servers.** { *; }

# Extension manifests and provider classes are referenced by name from the catalog.
-keep class site.anikitty.app.data.extensions.** { *; }
-keep class site.anikitty.extensions.** { *; }

# Cast: AniKittyCastOptionsProvider is instantiated by class name from the merged
# manifest meta-data (com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME),
# which R8 cannot see. Without this keep rule the release APK strips the class and
# Cast never initialises (the button says "Cast didn't start"); debug builds work
# only because they are not minified. Keep the whole package so the options
# provider, the session service, and the relay all survive shrinking.
-keep class site.anikitty.app.cast.** { *; }
-keepattributes *Annotation*
-dontwarn com.google.android.gms.cast.**
-dontwarn com.google.android.gms.cast.framework.**

-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-dontwarn okhttp3.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn com.google.android.gms.**
