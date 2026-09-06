package world.taqwa.app.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.settings.appContext
import kotlin.coroutines.resume

/** Matches the iOS provider's budget, so both platforms give up on a silent GPS at the same point. */
private const val FIX_TIMEOUT_MILLIS = 8_000L

private class AndroidLocationProvider : LocationProvider {

    override suspend fun permission(): LocationPermission {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) LocationPermission.GRANTED else LocationPermission.NOT_REQUESTED
    }

    override suspend fun requestPermission(): LocationPermission {
        // The Activity-scoped request is driven from Compose via rememberLauncherForActivityResult
        // in OnboardingScreen; by the time this is called the result is already reflected here.
        return permission()
    }

    /**
     * A cached fix if there is one, otherwise a fresh one.
     *
     * The cache alone was not enough: a fresh install on a phone that has been indoors, or any
     * emulator without a seeded location, has no last-known fix from either provider, so the user
     * who had just granted the permission was shown "No location yet" with no explanation. iOS
     * already requests a fix when its cache is empty; this matches it, and its 8 s budget.
     */
    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        lastKnown(lm)?.let { return it.latitude to it.longitude }
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { requestFreshFix(lm) }
        return fresh?.let { it.latitude to it.longitude }
    }

    private fun lastKnown(lm: LocationManager): Location? = listOfNotNull(
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
    ).maxByOrNull { it.time }

    /**
     * Only providers the *coarse* permission can legally read: `GPS_PROVIDER` requires
     * `ACCESS_FINE_LOCATION`, which this app deliberately does not declare, and asking for it
     * anyway throws `SecurityException`. `FUSED_PROVIDER` exists from API 31 and is the better
     * answer where it is available.
     */
    private fun coarseProviders(lm: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    private suspend fun requestFreshFix(lm: LocationManager): Location? {
        val provider = coarseProviders(lm).firstOrNull() ?: return null
        return suspendCancellableCoroutine { cont ->
            // LocationManagerCompat routes to getCurrentLocation on API 30+ and to a one-shot
            // single-update listener below it, so one call covers minSdk 26 upwards.
            val signal = CancellationSignal()
            cont.invokeOnCancellation { runCatching { signal.cancel() } }
            val delivered = runCatching {
                LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    signal,
                    { it.run() },
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
            if (delivered.isFailure && cont.isActive) cont.resume(null)
        }
    }
}

actual fun createLocationProvider(): LocationProvider = AndroidLocationProvider()
