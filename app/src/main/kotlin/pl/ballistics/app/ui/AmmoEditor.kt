package pl.ballistics.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.BulletLibrary
import pl.ballistics.app.data.DragType
import pl.ballistics.app.data.withBullet
import pl.ballistics.app.data.withFactoryLoad

private enum class Picker { BULLET, FACTORY }

@Composable
fun AmmoEditor(
    initial: AmmoProfile,
    isNew: Boolean,
    onSave: (AmmoProfile) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var a by remember { mutableStateOf(initial) }
    var picker by remember { mutableStateOf<Picker?>(null) }

    EditorScaffold(
        title = if (isNew) "Nowa amunicja" else a.name,
        canDelete = !isNew,
        onSave = { onSave(a) },
        onDelete = onDelete,
        onClose = onClose,
    ) {
        Section("Źródło danych") {
            Text(
                "Wybierz amunicję fabryczną albo sam pocisk (do elaboracji) z danych producentów, potem popraw prędkość wg chronografu.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = Picker.FACTORY }) { Text("Fabryczna") }
                OutlinedButton(onClick = { picker = Picker.BULLET }) { Text("Pocisk") }
            }
        }

        Section("Pocisk") {
            TextInput("Nazwa", a.name) { a = a.copy(name = it) }
            Choice("Model oporu", a.dragType, DragType.entries, { if (it == DragType.CUSTOM) "Własna Cd(M)" else it.name }) {
                a = a.copy(dragType = it)
            }
            if (a.dragType == DragType.CUSTOM) {
                Text(
                    "Wklej krzywą oporu z pomiarów Dopplera: w każdym wierszu liczba Macha i Cd, np. \"0.80 0.157\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = a.customCurve,
                    onValueChange = { a = a.copy(customCurve = it) },
                    label = { Text("Mach  Cd") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            } else {
                NumberField("Współczynnik balistyczny ${a.dragType.name}", a.bc, "lb/in²") { a = a.copy(bc = it) }
            }
            FieldRow { m ->
                NumberField("Masa", a.massGr, "gr", m) { a = a.copy(massGr = it) }
                NumberField("Średnica", a.diameterMm, "mm", m) { a = a.copy(diameterMm = it) }
            }
            NumberField("Długość pocisku (stabilność, znos)", a.lengthMm, "mm", optional = true) { a = a.copy(lengthMm = it) }
        }

        Section("Prędkość") {
            NumberField("Prędkość początkowa", a.muzzleVelocity, "m/s") { a = a.copy(muzzleVelocity = it) }
            FieldRow { m ->
                NumberField("Czułość prochu", a.powderSensitivity, "m/s/°C", m) { a = a.copy(powderSensitivity = it) }
                NumberField("przy temp.", a.powderReferenceTemp, "°C", m) { a = a.copy(powderReferenceTemp = it) }
            }
        }

        Section("Notatki") {
            OutlinedTextField(
                value = a.notes,
                onValueChange = { a = a.copy(notes = it) },
                placeholder = { Text("np. elaboracja, partia, długość lufy") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (picker) {
        Picker.BULLET -> LibraryDialog(
            title = "Pociski",
            options = BulletLibrary.bullets,
            label = { it.label },
            detail = { b -> listOfNotNull(b.g1?.let { "G1 $it" }, b.g7?.let { "G7 $it" }, "kal. ${b.diameterIn}\"").joinToString(" · ") },
            onPick = { a = a.withBullet(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.FACTORY -> LibraryDialog(
            title = "Amunicja fabryczna",
            options = BulletLibrary.factoryLoads,
            label = { it.label },
            detail = { "${it.muzzleVelocityFps.toInt()} fps (lufa testowa producenta)" },
            onPick = { a = a.withFactoryLoad(it); picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun <T> LibraryDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    detail: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter { label(it).contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Szukaj") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { item ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 8.dp)) {
                            Text(label(item), style = MaterialTheme.typography.bodyMedium)
                            Text(detail(item), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
                Text(
                    "Dane katalogowe producentów; sprawdź BC i prędkość z aktualnym katalogiem i chronografem.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
