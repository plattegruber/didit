# Navigation 3 keys are restored through kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class com.plattegruber.didit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
