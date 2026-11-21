package world.taqwa.app.feature.prayer

import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSUserDefaults

/** The defaults key the resources' language follows on iOS. */
internal const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * [block] with the resources in [tag]'s language, and the process back on its own languages after.
 *
 * It swaps `AppleLanguages` in this process's argument domain, where `-AppleLanguages (ar)` on a
 * command line lands. `NSLocale.preferredLanguages`, and so the resources' language, follows it on
 * iOS, and it is never written down, so it ends with the case or at worst with the process. Not
 * `NSUserDefaults.setObject`: that goes to the preferences file named for this test binary
 * (`test.kexe.plist`, the same for all three modules on the simulator), its removal can miss the
 * disk even with `synchronize()`, and the next test process then starts in that language. Not
 * `registerDefaults`: the simulator's global domain says en-US and outranks it.
 * (PrayerRowLanguageTest holds the swap itself: it writes nothing and ends with its case.)
 */
internal inline fun <T> inLanguage(tag: String, block: () -> T): T {
    val defaults = NSUserDefaults.standardUserDefaults
    val arguments = defaults.volatileDomainForName(NSArgumentDomain)
    defaults.removeVolatileDomainForName(NSArgumentDomain)
    defaults.setVolatileDomain(arguments + (APPLE_LANGUAGES to listOf(tag)), forName = NSArgumentDomain)
    try {
        return block()
    } finally {
        defaults.removeVolatileDomainForName(NSArgumentDomain)
        defaults.setVolatileDomain(arguments, forName = NSArgumentDomain)
    }
}
