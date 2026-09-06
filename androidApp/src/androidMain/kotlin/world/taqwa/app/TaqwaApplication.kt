package world.taqwa.app

import android.app.Application
import world.taqwa.app.notifications.notificationSmallIconResId
import world.taqwa.app.settings.appContext

class TaqwaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        notificationSmallIconResId = R.drawable.ic_stat_taqwa
    }
}
