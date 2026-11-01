package world.taqwa.app.recitation

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who may connect to the exported recitation session, and with what (security review A1/C4).
 * The cases are the controllers Media3 1.11.1 actually hands `onConnect`; see [controllerAccess].
 */
class ControllerAccessTest {

    private val unasked: () -> Boolean = { error("visibility asked of a controller already decided") }

    private fun access(
        notification: Boolean = false,
        thisApp: Boolean = false,
        trusted: Boolean = false,
        visible: () -> Boolean,
    ) = controllerAccess(notification, thisApp, trusted, visible)

    @Test
    fun theMediaNotificationControllerKeepsEverythingButTheTimeline() {
        // It is this app's own package too; spec §18.2 is why the timeline stays off it.
        val notification = access(notification = true, thisApp = true, trusted = true, visible = unasked)
        assertEquals(ControllerAccess.NOTIFICATION, notification)
        assertEquals(listOf(Player.COMMAND_GET_TIMELINE), notification.withheld)
    }

    @Test
    fun thisAppsOwnControllerKeepsEverything() {
        val own = access(thisApp = true, visible = unasked)
        assertEquals(ControllerAccess.FULL, own)
        assertTrue(own.withheld.isEmpty())
    }

    @Test
    fun aControllerMedia3TrustsKeepsEverything() {
        // SystemUI before the notification controller is up, Bluetooth, an enabled notification
        // listener or a MEDIA_CONTENT_CONTROL holder this app can see.
        assertEquals(ControllerAccess.FULL, access(trusted = true, visible = unasked))
    }

    @Test
    fun anUntrustedAppThatBoundToTheServiceIsRefused() {
        // Binding makes an app visible to this one, so Media3's verdict on it is a real one.
        assertEquals(ControllerAccess.REFUSED, access(visible = { true }))
    }

    @Test
    fun aControllerMedia3CouldNotCheckGetsTheTransportButNotTheQueue() {
        // A watch companion's transport controls on Android 11+, or a legacy controller with no
        // package: Media3 could not look it up, so its "untrusted" proves nothing.
        val limited = access(visible = { false })
        assertEquals(ControllerAccess.LIMITED, limited)
        assertEquals(
            setOf(
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
            ),
            limited.withheld.toSet(),
        )
        for (transport in listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT,
        )) {
            assertFalse(transport in limited.withheld, "command $transport withheld")
        }
    }
}
