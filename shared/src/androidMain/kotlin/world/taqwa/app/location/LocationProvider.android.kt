package world.taqwa.app.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import world.taqwa.app.settings.appContext

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

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        val last = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        ).maxByOrNull { it.time }
        return last?.let { it.latitude to it.longitude }
    }
}

actual fun createLocationProvider(): LocationProvider = AndroidLocationProvider()
