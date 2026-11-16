# Keep rules for AndroidX Startup & WorkManager reflection
-keep class androidx.startup.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class **_Impl {
    public <init>(...);
}
