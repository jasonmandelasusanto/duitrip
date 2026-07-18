# Keep Firestore model classes (fields are (de)serialized reflectively).
-keepclassmembers class com.duitrip.app.data.model.** {
  <init>();
  <fields>;
}
-keep class com.duitrip.app.data.model.** { *; }

# Firestore / Firebase
-keepattributes Signature
-keepattributes *Annotation*
