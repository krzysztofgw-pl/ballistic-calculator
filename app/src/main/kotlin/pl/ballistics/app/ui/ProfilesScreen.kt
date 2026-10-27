package pl.ballistics.app.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AppData
import pl.ballistics.app.data.SyncState
import pl.ballistics.app.data.WeaponProfile
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ProfilesScreen(
    data: AppData,
    onSelectWeapon: (String) -> Unit,
    onEditWeapon: (WeaponProfile) -> Unit,
    onEditAmmo: (AmmoProfile) -> Unit,
    onExport: () -> String,
    onImport: (String) -> Boolean,
    sync: SyncState,
    onSignIn: suspend (Context) -> String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(onExport().toByteArray()) } != null
        }.getOrDefault(false)
        Toast.makeText(context, if (ok) "Zapisano profile" else "Nie udało się zapisać pliku", Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        val ok = text != null && onImport(text)
        Toast.makeText(context, if (ok) "Wczytano profile" else "To nie jest plik profili", Toast.LENGTH_SHORT).show()
    }
    val active = data.activeWeapon

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Broń") {
            data.weapons.forEach { w ->
                ProfileRow(
                    title = w.name,
                    subtitle = listOf(w.caliber, w.scopeName, data.ammoFor(w)?.name ?: "brak amunicji")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    selected = w.id == active?.id,
                    onSelect = { onSelectWeapon(w.id) },
                    onEdit = { onEditWeapon(w) },
                )
            }
            TextButton(onClick = { onEditWeapon(WeaponProfile(ammoId = data.ammo.firstOrNull()?.id)) }) {
                Icon(Icons.Filled.Add, null)
                Text("Dodaj broń")
            }
        }

        Section("Amunicja") {
            data.ammo.forEach { a ->
                ProfileRow(
                    title = a.name,
                    subtitle = "${a.dragType.name} ${a.bc} · ${a.massGr} gr · ${a.muzzleVelocity} m/s",
                    selected = null,
                    onSelect = { onEditAmmo(a) },
                    onEdit = { onEditAmmo(a) },
                )
            }
            TextButton(onClick = { onEditAmmo(AmmoProfile()) }) {
                Icon(Icons.Filled.Add, null)
                Text("Dodaj amunicję")
            }
        }

        Section("Synchronizacja") {
            when (sync) {
                SyncState.Unavailable -> Unit
                SyncState.SignedOut -> {
                    Text(
                        "Zaloguj się kontem Google, a profile będą same synchronizować się między Twoimi telefonami.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = {
                        scope.launch {
                            onSignIn(context)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Zaloguj przez Google") }
                }
                is SyncState.SignedIn -> {
                    Text("Chmura: ${sync.email ?: "zalogowano"}", style = MaterialTheme.typography.bodyMedium)
                    sync.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = onSignOut) { Text("Wyloguj") }
                }
            }
            Text(
                "Możesz też zapisać profile do pliku (np. na Dysku Google) i wczytać je na innym telefonie. " +
                    "Android dodatkowo sam robi kopię zapasową na Twoim koncie Google.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("balistyka-${LocalDate.now()}.json") }) { Text("Eksportuj") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Importuj")
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, selected: Boolean?, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        if (selected != null) RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edytuj") }
    }
}
