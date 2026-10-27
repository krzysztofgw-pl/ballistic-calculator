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
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.BulletLibrary
import pl.ballistics.app.data.Cartridges
import pl.ballistics.app.toNumber
import pl.ballistics.app.data.DragType
import pl.ballistics.app.data.WeaponProfile
import pl.ballistics.app.data.withBullet
import pl.ballistics.app.data.withDragType
import pl.ballistics.app.data.withFactoryLoad
import kotlin.math.roundToInt

private enum class Picker { BULLET, FACTORY }

@Composable
fun AmmoEditor(
    initial: AmmoProfile,
    weapons: List<WeaponProfile>,
    isNew: Boolean,
    onSave: (AmmoProfile) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var a by remember { mutableStateOf(initial) }
    var picker by remember { mutableStateOf<Picker?>(null) }

    EditorScaffold(
        title = if (isNew) stringResource(R.string.title_new_load) else a.name,
        canDelete = !isNew,
        onSave = { onSave(a) },
        onDelete = onDelete,
        onClose = onClose,
    ) {
        Section(stringResource(R.string.section_data_source)) {
            Text(
                stringResource(R.string.hint_data_source),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = Picker.FACTORY }) { Text(stringResource(R.string.action_factory_load)) }
                OutlinedButton(onClick = { picker = Picker.BULLET }) { Text(stringResource(R.string.action_bullet)) }
            }
        }

        Section(stringResource(R.string.section_bullet)) {
            TextInput(stringResource(R.string.label_name), a.name) { a = a.copy(name = it) }
            CaliberField(
                stringResource(R.string.label_cartridge),
                a.cartridge,
                a.diameterMm.toNumber()?.div(25.4),
                stringResource(R.string.cartridge_any),
            ) { a = a.copy(cartridge = it) }
            Choice(stringResource(R.string.label_drag_model), a.dragType, DragType.entries, { if (it == DragType.CUSTOM) stringResource(R.string.drag_custom) else it.name }) {
                a = a.withDragType(it)
            }
            if (a.dragType == DragType.CUSTOM) {
                Text(
                    stringResource(R.string.hint_custom_curve),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = a.customCurve,
                    onValueChange = { a = a.copy(customCurve = it) },
                    label = { Text("Mach  Cd") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            } else {
                NumberField(stringResource(R.string.label_bc, a.dragType.name), a.bc, "lb/in²") { a = a.copy(bc = it) }
                Text(
                    stringResource(R.string.hint_bc_model, a.dragType.name),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FieldRow { m ->
                NumberField(stringResource(R.string.label_mass), a.massGr, "gr", m) { a = a.copy(massGr = it) }
                NumberField(stringResource(R.string.label_diameter), a.diameterMm, "mm", m) { a = a.copy(diameterMm = it) }
            }
            NumberField(stringResource(R.string.label_bullet_length), a.lengthMm, "mm", optional = true) { a = a.copy(lengthMm = it) }
        }

        Section(stringResource(R.string.section_velocity)) {
            FieldRow { m ->
                NumberField(stringResource(R.string.label_muzzle_velocity), a.muzzleVelocity, "m/s", m) { a = a.copy(muzzleVelocity = it) }
                NumberField(stringResource(R.string.label_test_barrel), a.testBarrelCm, "cm", m, optional = true) { a = a.copy(testBarrelCm = it) }
            }
            Text(
                stringResource(R.string.hint_reference_velocity),
                style = MaterialTheme.typography.bodySmall,
            )
            weapons.mapNotNull { w -> w.measuredVelocity(a)?.let { w to it } }.forEach { (w, mv) ->
                Text(
                    stringResource(R.string.measured_in_rifle, w.name, mv),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FieldRow { m ->
                NumberField(stringResource(R.string.label_powder_sensitivity), a.powderSensitivity, "m/s/°C", m) { a = a.copy(powderSensitivity = it) }
                NumberField(stringResource(R.string.label_at_temperature), a.powderReferenceTemp, "°C", m) { a = a.copy(powderReferenceTemp = it) }
            }
        }

        Section(stringResource(R.string.section_notes)) {
            OutlinedTextField(
                value = a.notes,
                onValueChange = { a = a.copy(notes = it) },
                placeholder = { Text(stringResource(R.string.hint_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (picker) {
        Picker.BULLET -> LibraryDialog(
            title = stringResource(R.string.title_bullets),
            options = BulletLibrary.bullets,
            label = { it.label },
            group = { Cartridges.boreLabel(it.diameterIn) },
            initialGroup = Cartridges.find(a.cartridge)?.let { Cartridges.boreLabel(it.bulletDiameterIn) },
            detail = { b ->
                listOfNotNull(
                    b.g1?.let { "G1 $it" },
                    b.g7?.let { "G7 $it" },
                    b.lengthIn?.let { stringResource(R.string.bullet_length, it) },
                    b.twistIn?.let { stringResource(R.string.bullet_twist, if (it % 1.0 == 0.0) it.toInt() else it) },
                ).joinToString(" · ")
            },
            onPick = { a = a.withBullet(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.FACTORY -> LibraryDialog(
            title = stringResource(R.string.title_factory_loads),
            options = BulletLibrary.factoryLoads.sortedWith(compareBy({ it.bullet.diameterIn }, { it.cartridge })),
            label = { it.label },
            group = { it.cartridge },
            initialGroup = Cartridges.find(a.cartridge)?.name,
            detail = {
                stringResource(
                    R.string.factory_load_velocity,
                    it.muzzleVelocityMps.roundToInt(),
                    it.muzzleVelocityFps.roundToInt(),
                    it.testBarrelCm.roundToInt(),
                    (it.testBarrelCm / 2.54).roundToInt(),
                )
            },
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
    group: (T) -> String,
    initialGroup: String?,
    detail: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(options) { options.map(group).distinct() }
    var selectedGroup by remember { mutableStateOf(initialGroup?.takeIf { it in groups }) }
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtered = options.filter { o ->
        (selectedGroup == null || group(o) == selectedGroup) && label(o).let { l -> words.all { l.contains(it, ignoreCase = true) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.label_library_caliber), selectedGroup, listOf<String?>(null) + groups, {
                    it ?: stringResource(R.string.all_calibers)
                }) { selectedGroup = it }
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.action_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    filtered.groupBy(group).forEach { (name, rows) ->
                        item(key = "group:$name") {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(rows) { item ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 8.dp)) {
                                Text(label(item), style = MaterialTheme.typography.bodyMedium)
                                Text(detail(item), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Text(
                    stringResource(R.string.hint_library_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
