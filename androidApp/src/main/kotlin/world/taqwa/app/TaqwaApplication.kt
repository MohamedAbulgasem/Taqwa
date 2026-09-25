package world.taqwa.app

import android.app.Application
import world.taqwa.app.crash.crashLogStore
import world.taqwa.app.crash.installCrashHandler
import world.taqwa.app.notifications.NotificationTopUpWorker
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext
import world.taqwa.app.widget.TaqwaWidgets
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.androidAyahWidgetUpdateHook
import world.taqwa.app.widget.androidWidgetPinHook
import android.appwidget.AppWidgetManager
import world.taqwa.app.widget.androidWidgetPlacementHook
import world.taqwa.app.widget.androidWidgetUpdateHook
import world.taqwa.app.widget.anyAyahWidgetPlaced
import world.taqwa.app.widget.anyWidgetPlaced

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // First, so that a crash anywhere below is already recorded (crash spec §2).
        installCrashHandler(crashLogStore)
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
        androidWidgetUpdateHook = { TaqwaWidgets.updateAll(applicationContext) }
        androidAyahWidgetUpdateHook = { TaqwaWidgets.updateAyah(applicationContext) }
        androidWidgetPinHook = { widget -> TaqwaWidgets.requestPin(applicationContext, widget) }
        // The same two queries the refresh alarms already trust to decide whether to keep
        // ticking — read on demand, never cached, because widgets come and go outside the app.
        // A device with no app-widget host at all — a kiosk launcher, some work profiles — has no
        // AppWidgetManager, and the two queries below answer "not placed" for want of anywhere to
        // look. That is the one answer this must never give: it would offer to add a widget the
        // launcher cannot hold. Unknown, which offers nothing, is the honest reading.
        androidWidgetPlacementHook = {
            if (AppWidgetManager.getInstance(applicationContext) == null) {
                WidgetPlacement.Unknown
            } else {
                WidgetPlacement(
                    prayer = anyWidgetPlaced(applicationContext),
                    ayah = anyAyahWidgetPlaced(applicationContext),
                )
            }
        }
        // Unlocking is when the home screen is actually read, so it is when the countdown most
        // needs to be current. Runtime registration is not a choice: ACTION_USER_PRESENT is one of
        // the broadcasts the platform refuses to deliver to manifest-declared receivers.
        TaqwaWidgets.registerUnlockRefresh(this)
        // The twice-daily rebuild of the alarm plan, for the cases no broadcast covers; see the
        // worker. WorkManager is already initialised here for the widgets and the downloads.
        NotificationTopUpWorker.ensureScheduled(this)
    }
}
