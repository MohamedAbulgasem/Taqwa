package world.taqwa.app

import android.app.Application
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext
import world.taqwa.app.widget.TaqwaWidgets
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.androidAyahWidgetUpdateHook
import world.taqwa.app.widget.androidWidgetPinHook
import world.taqwa.app.widget.androidWidgetPlacementHook
import world.taqwa.app.widget.androidWidgetUpdateHook
import world.taqwa.app.widget.anyAyahWidgetPlaced
import world.taqwa.app.widget.anyWidgetPlaced

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
        androidWidgetUpdateHook = { TaqwaWidgets.updateAll(applicationContext) }
        androidAyahWidgetUpdateHook = { TaqwaWidgets.updateAyah(applicationContext) }
        androidWidgetPinHook = { widget -> TaqwaWidgets.requestPin(applicationContext, widget) }
        // The same two queries the refresh alarms already trust to decide whether to keep
        // ticking — read on demand, never cached, because widgets come and go outside the app.
        androidWidgetPlacementHook = {
            WidgetPlacement(
                prayer = anyWidgetPlaced(applicationContext),
                ayah = anyAyahWidgetPlaced(applicationContext),
            )
        }
        // Unlocking is when the home screen is actually read, so it is when the countdown most
        // needs to be current. Runtime registration is not a choice: ACTION_USER_PRESENT is one of
        // the broadcasts the platform refuses to deliver to manifest-declared receivers.
        TaqwaWidgets.registerUnlockRefresh(this)
    }
}
