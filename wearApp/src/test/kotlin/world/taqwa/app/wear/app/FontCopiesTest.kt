package world.taqwa.app.wear.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The watch app's Manrope is the phone's, byte for byte (the Lead's call 2): `:wearApp` does not
 * link `:shared`, so the four weights the screens use are copies, held here to the phone's files.
 */
class FontCopiesTest {

    private val watchFonts = File(System.getProperty("taqwa.wearRes") ?: "src/main/res", "font")
    private val phoneFonts = File(System.getProperty("taqwa.phoneFonts") ?: "../shared/src/commonMain/composeResources/font")

    @Test
    fun eachManropeIsThePhonesFile() {
        val copies = mapOf(
            "manrope_light.ttf" to "Manrope-Light.ttf",
            "manrope_regular.ttf" to "Manrope-Regular.ttf",
            "manrope_semibold.ttf" to "Manrope-SemiBold.ttf",
            "manrope_extrabold.ttf" to "Manrope-ExtraBold.ttf",
        )
        assertEquals(copies.keys, watchFonts.listFiles().orEmpty().map { it.name }.toSet(), "the four weights and nothing else")
        for ((watch, phone) in copies) {
            assertContentEquals(File(phoneFonts, phone).readBytes(), File(watchFonts, watch).readBytes(), "$watch is $phone")
        }
    }
}
