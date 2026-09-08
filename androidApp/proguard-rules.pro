# Taqwa release rules. Compose, SQLDelight, DataStore, Glance and adhan2 ship consumer rules; only
# what is reached by name from outside the code is listed here.

# Manifest-registered entry points (AGP keeps these itself; stated explicitly).
-keep class world.taqwa.app.TaqwaApplication { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# WorkManager (pulled in by Glance) opens its Room database by reflection on the "_Impl" class
# name; without these the app died in androidx.startup before its first frame.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.** { *; }
-keep class * implements androidx.startup.Initializer { <init>(); }

# Keep line numbers so a crash report from a release build still points at a source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager instantiates input mergers and workers by class name with a no-argument or
# (Context, WorkerParameters) constructor; R8 stripped OverwritingInputMerger's constructor and
# Glance's session worker never ran, leaving both widgets blank ("Could not create Input Merger").
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
