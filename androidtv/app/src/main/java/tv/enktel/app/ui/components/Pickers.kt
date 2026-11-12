package tv.enktel.app.ui.components

import androidx.activity.result.ActivityResultLauncher

/**
 * Open a system screen — a file picker, a folder picker, a permission prompt —
 * or say that this device does not have one.
 *
 * `launch` hands an intent to whatever on the device answers it, and throws
 * `ActivityNotFoundException` when nothing does. On a phone something always
 * does. On a Fire TV stick and on a good share of Android TV boxes nothing
 * does: they ship without the Storage Access Framework's document picker, so
 * "Import file" in Settings — three buttons, in fact — ended the app the
 * moment it was pressed, on exactly the devices most of this app's viewers
 * own.
 *
 * So no call site launches directly. `PickerLaunchTest` reads the sources and
 * fails on any `.launch(` made on a launcher outside this file, because the
 * mistake is invisible on every device a developer is likely to have in hand.
 *
 * `Exception`, not just the one type: a launcher can also refuse because its
 * registration has gone (`IllegalStateException`) or the platform declines the
 * intent (`SecurityException`), and none of those is worth a crash over a
 * button that could simply explain itself.
 *
 * @return whether the screen was opened.
 */
fun <I> ActivityResultLauncher<I>.launchOr(input: I, onUnavailable: () -> Unit): Boolean =
    try {
        launch(input)
        true
    } catch (_: Exception) {
        onUnavailable()
        false
    }

/** What each button says when there is nothing to open. Wording lives here so it stays one voice. */
object PickerMissing {
    const val IMPORT = "This device has no file picker. Use Add playlist and enter the address instead."
    const val EXPORT = "This device has no file picker, so there is nowhere to save the file."
    const val FOLDER = "This device has no folder picker. Downloads stay in the app's own folder."
    const val MICROPHONE = "This device can't ask for microphone access. Allow it in the system settings."
}
