package world.taqwa.app.widget

/** Nudges the platform to redraw its widgets sooner than the next periodic tick, after the
 * mirror has just been written with fresher data. */
expect fun refreshWidgets()
