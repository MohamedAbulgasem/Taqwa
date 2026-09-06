package world.taqwa.app.notifications

import world.taqwa.app.settings.appContext

actual fun createNotificationScheduler(): NotificationScheduler = AndroidNotificationScheduler(appContext)
