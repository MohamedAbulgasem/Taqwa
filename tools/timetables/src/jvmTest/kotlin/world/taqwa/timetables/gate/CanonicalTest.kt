package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.ClockRule
import world.taqwa.app.prayer.engine.method.ClockTimes
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.authorities.IrnArctic
import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The stamps' fingerprint ([Stamps.fingerprint], in [Canonical] words) is the same on every machine and still
 * moves with every real change of a method.
 *
 * It once printed each method through its data class's `toString`, every double with every digit. The curves a
 * method carries are computed with `sin` and `cos`, whose last bits HotSpot's own routines give differently on
 * each CPU, so the cloud's Linux runner and the Mac wrote different `engineHash`es for the same twelve curve
 * entries, each rewriting the other's (BUILD-LOG, 6 October), and `checkStamps` called a stamp stale after
 * every cloud `prove`. With the maths routines switched off on this Mac (the JDK's portable ones instead), the
 * old fingerprint moved those twelve hashes alone, through 46,381 values up to 4e-14 of a degree apart; the
 * canonical one moves none.
 *
 * The samples: Paris (fr.gmp: computed Fajr and Isha curves and an end-of-eating curve), Tromsø (no.irn: IRN's
 * clock rule), Astana (kz.qmdb: its curves at a user's latitude) and Tataouine (tn.inm: monthly offsets).
 */
class CanonicalTest {

    private fun method(entry: String, place: Place): TimetableMethod =
        requireNotNull(Registry.resolveEntry(requireNotNull(Registry.byId(entry)), place).method) { "$entry: no method at $place" }

    private val paris = method("fr.gmp", Place(48.8418, 2.3556, "Europe/Paris", "FR"))
    private val tromso = method("no.irn", Place(69.6489, 18.95508, "Europe/Oslo", "NO"))
    private val astana = method("kz.qmdb", Place(51.13, 71.43, "Asia/Almaty", "KZ"))
    private val tataouine = method("tn.inm", Place(32.933, 10.45, "Africa/Tunis", "TN"))

    private fun fp(method: TimetableMethod) = Stamps.fingerprint(method)

    private fun DoubleArray.with(slot: Int, delta: Double) = copyOf().also { it[slot] += delta }

    private val DoubleArray?.required: DoubleArray get() = requireNotNull(this) { "the sample has no curve" }

    private val TimetableMethod.endCurve: DoubleArray get() = (endOfEating as EndOfEating.DawnAngle).bySlot.required

    private fun TimetableMethod.withEndCurve(curve: DoubleArray) = copy(endOfEating = (endOfEating as EndOfEating.DawnAngle).copy(bySlot = curve))

    @Test
    fun `the samples carry what they are meant to test`() {
        assertTrue(paris.fajrAngleByDayOfYear != null && paris.ishaAngleByDayOfYear != null && paris.endCurve.size == 366)
        assertTrue(tromso.clockRule is IrnArctic)
        assertTrue(astana.fajrAngleByDayOfYear != null)
        assertTrue(tataouine.monthlyOffsets?.containsKey(Prayer.ASR) == true)
    }

    @Test
    fun `a doubles last bits leave the fingerprint as it is`() {
        // Each value moved by up to 16 units in its last place, alternately up and down: four times the widest
        // difference measured between two machines' maths routines (4e-14 of a degree, about 10 at 16 degrees).
        fun jitter(curve: DoubleArray) = DoubleArray(curve.size) { i ->
            curve[i] + (if (i % 2 == 0) 1 else -1) * ((i % 16) + 1) * Math.ulp(curve[i])
        }
        for (m in listOf(paris, astana, tromso)) {
            val moved = m.copy(
                fajrAngle = Math.nextUp(m.fajrAngle),
                horizonDeg = Math.nextDown(m.horizonDeg),
                fajrAngleByDayOfYear = m.fajrAngleByDayOfYear?.let(::jitter),
                ishaAngleByDayOfYear = m.ishaAngleByDayOfYear?.let(::jitter),
                fixedPoint = m.fixedPoint?.let { GeoPoint(Math.nextUp(it.lat), Math.nextDown(it.lon)) },
            )
            val end = m.endOfEating
            val endCurve = (end as? EndOfEating.DawnAngle)?.bySlot
            val movedEnd = if (endCurve != null) moved.withEndCurve(jitter(endCurve)) else moved
            assertEquals(fp(m), fp(movedEnd), "${m.id}: moved in its last bits")
        }
        assertEquals(Canonical.of(GeoPoint(48.8418, 2.3556)), Canonical.of(GeoPoint(Math.nextUp(48.8418), Math.nextDown(2.3556))))
    }

    @Test
    fun `a margin an angle and one curve slot each move the fingerprint`() {
        val base = fp(paris)
        val changes = mapOf(
            "a margin by a second" to paris.copy(margins = paris.margins.copy(fajr = paris.margins.fajr + 1)),
            "the authority's minutes by one" to paris.copy(authorityMinutes = paris.authorityMinutes.copy(isha = paris.authorityMinutes.isha + 1)),
            "the Fajr angle by a hundredth" to paris.copy(fajrAngle = paris.fajrAngle + 0.01),
            "the horizon by a hundredth" to paris.copy(horizonDeg = paris.horizonDeg - 0.01),
            "one Fajr slot by a thousandth" to paris.copy(fajrAngleByDayOfYear = paris.fajrAngleByDayOfYear.required.with(100, 0.001)),
            "one Fajr slot by a millionth" to paris.copy(fajrAngleByDayOfYear = paris.fajrAngleByDayOfYear.required.with(100, 1e-6)),
            "one Isha slot by a thousandth" to paris.copy(ishaAngleByDayOfYear = paris.ishaAngleByDayOfYear.required.with(300, -0.001)),
            "one end-of-eating slot by a thousandth" to paris.withEndCurve(paris.endCurve.with(200, 0.001)),
            "the end-of-eating angle" to paris.copy(endOfEating = (paris.endOfEating as EndOfEating.DawnAngle).copy(degrees = 18.5)),
            "the end of eating's margin" to paris.copy(endOfEatingMarginSeconds = paris.endOfEatingMarginSeconds + 1),
            "the fixed point by a ten-thousandth" to paris.copy(fixedPoint = GeoPoint(48.8419, 2.3556)),
            "a start point" to paris.copy(startPoints = listOf(GeoPoint(48.9, 2.4))),
            "the high-latitude rule" to paris.copy(highLatitude = HighLatRule.NightFraction(1.0 / 7, 1.0 / 7)),
        )
        for ((what, changed) in changes) assertNotEquals(base, fp(changed), "fr.gmp: $what")
        // Two different changes give two different fingerprints, too.
        assertEquals(changes.size, changes.values.map(::fp).toSet().size, "each change its own fingerprint")

        val rule = tromso.clockRule as IrnArctic
        assertNotEquals(fp(tromso), fp(tromso.copy(clockRule = rule.copy(fajrDepression = rule.fajrDepression + 0.01))), "no.irn: a depression")
        assertNotEquals(fp(tromso), fp(tromso.copy(clockRule = rule.copy(windowShiftDays = listOf(0)))), "no.irn: the window shifts")
        assertNotEquals(fp(tromso), fp(tromso.copy(clockRule = rule.copy(windows = rule.windows.dropLast(1)))), "no.irn: a window")
        assertNotEquals(fp(tromso), fp(tromso.copy(clockRule = null)), "no.irn: the clock rule")

        val asr = requireNotNull(tataouine.monthlyOffsets).getValue(Prayer.ASR)
        val monthly = requireNotNull(tataouine.monthlyOffsets) + (Prayer.ASR to asr.copyOf().also { it[7] += 1 })
        assertNotEquals(fp(tataouine), fp(tataouine.copy(monthlyOffsets = monthly)), "tn.inm: one month's Asr by a second")
    }

    @Test
    fun `doubles are whole millionths and records list every field by name`() {
        assertEquals("15635855", Canonical.number(15.635855125203712))
        assertEquals("-833300", Canonical.number(-0.8333))
        assertEquals("142857", Canonical.number(1.0 / 7))
        assertEquals("0", Canonical.number(-0.0))
        assertEquals("NaN", Canonical.number(Double.NaN))
        assertEquals("GeoPoint(lat=48841800,lon=2355600)", Canonical.of(GeoPoint(48.8418, 2.3556)))
        assertEquals("EndOfEating\$DawnAngle(bySlot=null,degrees=18000000)", Canonical.of(EndOfEating.DawnAngle(18.0, null)))
        assertTrue(Canonical.of(EndOfEating.DawnAngle(18.0, DoubleArray(366) { 17.88 })).startsWith("EndOfEating\$DawnAngle(bySlot=[17880000,17880000,"))
        assertEquals("EndOfEating\$SameAsFajrDawn()", Canonical.of(EndOfEating.SameAsFajrDawn))
        assertEquals("[1,2]", Canonical.of(intArrayOf(1, 2)))
        assertEquals("[\"a\\\"b\",Prayer.ASR,null]", Canonical.of(listOf("a\"b", Prayer.ASR, null)))
        // A set and a map, whatever order they were built in.
        assertEquals(Canonical.of(linkedSetOf(Prayer.ISHA, Prayer.FAJR)), Canonical.of(linkedSetOf(Prayer.FAJR, Prayer.ISHA)))
        assertEquals(Canonical.of(linkedMapOf(Prayer.ISHA to 1, Prayer.FAJR to 2)), Canonical.of(linkedMapOf(Prayer.FAJR to 2, Prayer.ISHA to 1)))
        assertEquals("2026-10-12", Canonical.of(LocalDate(2026, 10, 12)))
        // Every field of a method is written, by its own name.
        val words = fp(paris)
        for (field in TimetableMethod::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            assertTrue("${field.name}=" in words, "TimetableMethod.${field.name} is in the fingerprint")
        }
    }

    /** A record holding a function: its words are the compiler's, not the method's. */
    private data class WithLambda(val f: () -> Int)

    @Test
    fun `a lambda or an anonymous object in a method is refused not printed`() {
        val anonymous = object : ClockRule {
            override fun on(date: LocalDate, point: GeoPoint, zone: TimeZone): ClockTimes = error("never called")
        }
        assertFailsWith<IllegalArgumentException> { fp(paris.copy(clockRule = anonymous)) }
        assertFailsWith<IllegalArgumentException> { Canonical.of(WithLambda { 1 }) }
        assertFailsWith<IllegalArgumentException> { Canonical.of(lazy { 1 }) }
        assertFailsWith<IllegalArgumentException> { Canonical.of(Any()) }
    }

    @Test
    fun `no fingerprint of the manifest prints a double or an identity`() {
        val checkout = TestPaths.repoRoot.resolve("tools/timetables/official")
        val manifest = GateManifest.load(checkout.resolve("gate"))
        val all = CheckStamps.fingerprintsByEntry(manifest, OfficialRoots(File("/nonexistent/archive"), checkout))
        assertTrue(all.size > 60, "the manifest's entries: ${all.size}")
        val double = Regex("""\d\.\d|\d[eE]-?\d""")
        val identity = Regex("""@[0-9a-f]{4,}""")
        for ((entry, fps) in all) for (f in fps) {
            assertTrue(double.find(f) == null, "$entry: a double printed: ${double.find(f)?.let { f.substring(maxOf(0, it.range.first - 40), minOf(f.length, it.range.last + 40)) }}")
            assertTrue(identity.find(f) == null, "$entry: an identity printed: ${identity.find(f)?.value}")
        }
    }
}
