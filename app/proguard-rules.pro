# Navigation 3 keys are restored through kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class com.plattegruber.didit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance creates action callbacks by reflection from their class name, through the no-argument
# constructor. Its own rule keeps the classes but not the constructors, so R8 strips them and a
# tap on the widget silently does nothing.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
