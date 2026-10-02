# Room, Compose and kotlinx.serialization ship their own consumer rules. The backup file
# classes are only (de)serialized through their generated serializers, referenced directly,
# but keep them whole so an export can always be read back by the same build.
-keep,includedescriptorclasses class com.dojolog.data.backup.**$$serializer { *; }
-keepclassmembers class com.dojolog.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.dojolog.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
