# Room generated classes
-keep class com.afzal.rozaalarm.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Receivers / services referenced from the manifest only
-keep class com.afzal.rozaalarm.alarm.** { *; }

# Keep view-binding generated classes
-keep class com.afzal.rozaalarm.databinding.** { *; }
