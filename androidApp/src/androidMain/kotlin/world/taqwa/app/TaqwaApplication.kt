package world.taqwa.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext
import world.taqwa.app.widget.TaqwaMediumGlanceWidget
import world.taqwa.app.widget.TaqwaSmallGlanceWidget
import world.taqwa.app.widget.androidWidgetUpdateHook

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
        androidWidgetUpdateHook = {
            TaqwaSmallGlanceWidget().updateAll(applicationContext)
            TaqwaMediumGlanceWidget().updateAll(applicationContext)
        }
    }
}
