package com.emabuia.pokevault.data.trade

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

actual fun platformCoarseLocation(): CoarseLocationSource = IosCoarseLocation

/**
 * CoreLocation con precisione al chilometro e solo "mentre usi l'app": la
 * versione iOS di ACCESS_COARSE_LOCATION. Il gestore vive sul thread
 * principale, come vuole CoreLocation.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosCoarseLocation : CoarseLocationSource {
    private val manager by lazy {
        CLLocationManager().apply {
            desiredAccuracy = kCLLocationAccuracyKilometer
            delegate = Delegate
        }
    }

    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private var pendingLocation: CompletableDeferred<CLLocation?>? = null

    override fun permission(): Boolean? = when (CLLocationManager.authorizationStatus()) {
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> true
        kCLAuthorizationStatusNotDetermined -> null
        else -> false
    }

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        permission()?.let { return@withContext it }
        val answer = CompletableDeferred<Boolean>()
        pendingPermission = answer
        manager.requestWhenInUseAuthorization()
        answer.await()
    }

    override suspend fun currentPoint(timeoutMs: Long): Pair<Double, Double>? = withContext(Dispatchers.Main) {
        if (permission() != true) return@withContext null
        val result = CompletableDeferred<CLLocation?>()
        pendingLocation = result
        manager.requestLocation()
        val location = withTimeoutOrNull(timeoutMs) { result.await() } ?: manager.location
        pendingLocation = null
        location?.coordinate?.useContents { latitude to longitude }
    }

    private object Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        @ObjCSignatureOverride
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val last = didUpdateLocations.lastOrNull() as? CLLocation
            pendingLocation?.complete(last)
        }

        @ObjCSignatureOverride
        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            pendingLocation?.complete(null)
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val answer = permission() ?: return
            pendingPermission?.complete(answer)
            pendingPermission = null
        }
    }
}
