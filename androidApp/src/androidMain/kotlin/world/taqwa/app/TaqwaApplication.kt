package world.taqwa.app

import android.app.Application
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext
import world.taqwa.app.widget.TaqwaWidgets
import world.taqwa.app.widget.androidAyahWidgetUpdateHook
import world.taqwa.app.widget.androidWidgetPinHook
import world.taqwa.app.widget.androidWidgetUpdateHook

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
        androidWidgetUpdateHook = { TaqwaWidgets.updateAll(applicationContext) }
        androidAyahWidgetUpdateHook = { TaqwaWidgets.updateAyah(applicationContext) }
        androidWidgetPinHook = { TaqwaWidgets.requestPin(applicationContext) }
        // Unlocking is when the home screen is actually read, so it is when the countdown most
        // needs to be current. Runtime registration is not a choice: ACTION_USER_PRESENT is one of
        // the broadcasts the platform refuses to deliver to manifest-declared receivers.
        TaqwaWidgets.registerUnlockRefresh(this)
    }
}
