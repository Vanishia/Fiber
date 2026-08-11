# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Markwon's image module references optional GIF and SVG decoders. Fiber uses
# Coil for image loading and does not bundle these optional decoder libraries.
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException
-dontwarn pl.droidsonroids.gif.GifDrawable

# Markwon 4.6.2's image modules rely on callback objects that R8 can optimize
# incorrectly when used through the legacy Coil 0.13 adapter. The symptom is
# that release builds render only the Markdown image's alt text.
-keep class io.noties.markwon.image.** { *; }
