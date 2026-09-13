package world.taqwa.app.recitation

/**
 * Whether the app coming to the front while a recitation is *playing* should open the ayah being
 * recited (spec §15.5). Android says no: its media notification carries a tap of its own, which
 * arrives as a launch request, and an ordinary launch from the icon must not move the reader.
 * iOS says yes: the lock screen's Now Playing opens the app with no word about why, so the app
 * coming to the front with a voice going is the only signal there is - and, with the voice
 * still going, almost always what the person wants to see.
 */
internal expect val foregroundReturnsToRecitation: Boolean
