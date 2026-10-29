package pl.ballistics.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.ballistics.app.R
import pl.ballistics.app.data.AppUpdate

/** Checks the releases for a newer build and installs it; progress goes to toasts, failures to a dialog. */
class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    var offer by mutableStateOf<AppUpdate.Release?>(null)
    var error by mutableStateOf<String?>(null)
    private var busy = false

    /** Checks once per process on start, so turning the phone does not ask again. */
    fun checkOnStart() {
        if (checkedOnStart || !AppUpdate.isCiBuild(context)) return
        checkedOnStart = true
        check(asked = false)
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** A check on start stays quiet unless there is an update; a check the user asked for always answers. */
    fun check(asked: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { AppUpdate.newer(context) }
                .onSuccess { r -> if (r != null) offer = r else if (asked) toast(context.getString(R.string.toast_up_to_date)) }
                .onFailure { e -> if (asked) error = e.message ?: e.toString() }
            busy = false
        }
    }

    fun install(release: AppUpdate.Release) {
        offer = null
        if (!AppUpdate.canInstall(context)) {
            toast(context.getString(R.string.toast_allow_installs))
            AppUpdate.openInstallPermission(context)
            return
        }
        if (busy) return
        busy = true
        toast(context.getString(R.string.toast_downloading_update, release.build))
        scope.launch {
            runCatching { AppUpdate.download(context, release) }
                .onSuccess { AppUpdate.install(context, it) }
                .onFailure { e -> error = e.message ?: e.toString() }
            busy = false
        }
    }

    private companion object {
        var checkedOnStart = false
    }
}

@Composable
fun rememberAppUpdater(): AppUpdater {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { AppUpdater(context.applicationContext, scope) }
}

@Composable
fun AppUpdateDialogs(updater: AppUpdater) {
    updater.offer?.let { r ->
        AlertDialog(
            onDismissRequest = { updater.offer = null },
            title = { Text(stringResource(R.string.update_available, r.build)) },
            text = { if (r.notes.isNotBlank()) Text(r.notes) },
            confirmButton = { TextButton(onClick = { updater.install(r) }) { Text(stringResource(R.string.action_install)) } },
            dismissButton = { TextButton(onClick = { updater.offer = null }) { Text(stringResource(R.string.action_later)) } },
        )
    }
    updater.error?.let { e ->
        AlertDialog(
            onDismissRequest = { updater.error = null },
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(e) },
            confirmButton = { TextButton(onClick = { updater.error = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
