# R8 like the original (which used full mode with renaming, string encryption and a native libapp.so;
# those are NOT reproduced). Here R8 only shrinks the libraries so that the resource shrinker can drop
# unused library resources. The rebuilt app code is kept completely and unrenamed.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# App code: keep everything as written (reflection, Gson models, ViewBinding, DebugBridge in qa)
# (R classes excluded: keeping them would mark every resource as used)
-keep class !com.jcapps.adbremote.R,!com.jcapps.adbremote.R$*,com.jcapps.adbremote.** { *; }
-keep interface com.jcapps.adbremote.** { *; }
-keep enum com.jcapps.adbremote.** { *; }

# BouncyCastle (providers looked up by name)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
