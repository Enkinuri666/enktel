package tv.enktel.app.data

import android.app.Notification
import android.app.Service

/**
 * The two things every foreground service in this app has to survive, in one
 * place so that all three survive them the same way.
 *
 * ### Android may say no
 *
 * `startForeground` is a request, and it throws when refused. It is refused
 * for an app that is not on screen (Android 12 and later), and — the case that
 * was waiting for us — once a `dataSync` service has used its allowance.
 * Android 15 gives every app six hours of `dataSync` foreground time in any
 * twenty-four, and all three of ours are `dataSync`: recording, the download
 * keep-alive, and sending to a PC. A long recording or a big download queue
 * spends that, and from then on the next `startForeground` throws inside
 * `onStartCommand`, where nothing catches it. That is a crash on launch of the
 * very feature the viewer just asked for.
 *
 * So services call [enter] and decide what to do with a `false`.
 *
 * ### Android may say stop
 *
 * When the allowance runs out *while* a service is running, the system calls
 * `Service.onTimeout(startId, fgsType)` and gives it a few seconds to stop. A
 * service that does not is killed along with the app, with "A foreground
 * service of type dataSync did not stop within its timeout". None of ours
 * overrode it. Each now does, and winds down what it was doing first.
 *
 * `ForegroundServicesTest` reads the manifest and the sources and fails if a
 * service is declared without both of these, so a fourth service cannot be
 * added without them.
 */
object Foreground {

    /**
     * Put [service] in the foreground, or report that Android would not.
     *
     * Safe to give up after a `false`: the platform clears its "this service
     * promised to call startForeground" flag before it runs the checks that
     * throw, so stopping the service afterwards does not trip the separate
     * "did not then call Service.startForeground()" crash.
     */
    fun enter(service: Service, id: Int, notification: Notification): Boolean =
        try {
            service.startForeground(id, notification)
            true
        } catch (_: Exception) {
            false
        }
}
