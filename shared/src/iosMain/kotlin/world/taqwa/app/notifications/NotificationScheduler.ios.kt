package world.taqwa.app.notifications

actual fun createNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()
