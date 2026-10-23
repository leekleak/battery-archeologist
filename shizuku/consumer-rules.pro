# Shizuku API and our user service must keep stable names: the service
# component name is sent to the Shizuku server and resolved in our APK.
-keep class rikka.shizuku.** { *; }
-keep class com.leekleak.shizuku.BatteryArcheologistService { *; }
-keep class com.leekleak.shizuku.IBatteryArcheologistService { *; }
-keep class com.leekleak.shizuku.IBatteryArcheologistService$Stub { *; }
-keep class com.leekleak.shizuku.IOutputCallback { *; }
-keep class com.leekleak.shizuku.IOutputCallback$Stub { *; }
