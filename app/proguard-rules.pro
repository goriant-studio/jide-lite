# Intentionally minimal for MVP release builds.
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLStreamReader

# The app embeds D8 and calls it at runtime inside LocalJavaRunner.
# D8 resolves its threading module providers via Class.forName() on hard-coded
# names, so release obfuscation/removal breaks runtime with
# "Failure to find a provider for the threading module".
-keep class com.android.tools.r8.threading.providers.** { *; }
