package world.taqwa.app.notifications

/** iOS has no exact-alarm permission: `UNCalendarNotificationTrigger` fires at the stated time. */
actual fun canScheduleExactAlarms(): Boolean = true
