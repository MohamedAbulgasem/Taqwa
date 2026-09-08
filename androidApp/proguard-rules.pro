# Taqwa release rules. The libraries in use (Compose, SQLDelight, DataStore, Glance, adhan2)
# ship their own consumer rules; only what is reached by name from outside the code goes here.

# Broadcast receivers, the widget receivers and the Application class are named in the manifest;
# AGP keeps manifest-referenced classes on its own, this is the explicit statement of that.
-keep class world.taqwa.app.TaqwaApplication { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Keep line numbers so a crash report from a release build still points at a source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager (pulled in by Glance) opens its Room database by reflection on the "_Impl" class
# name; R8 removed it and the app died in androidx.startup before the first frame.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.** { *; }
-keep class * implements androidx.startup.Initializer { <init>(); }
