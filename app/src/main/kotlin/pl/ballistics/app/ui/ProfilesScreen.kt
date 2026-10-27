package pl.ballistics.app.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AppData
import pl.ballistics.app.data.Cartridges
import pl.ballistics.app.data.WeaponProfile
import pl.ballistics.app.data.caliberGroup
import pl.ballistics.app.data.diameterInOrMax
import java.util.Locale

@Composable
fun ProfilesScreen(
    data: AppData,
    onSelectWeapon: (String) -> Unit,
    onEditWeapon: (WeaponProfile) -> Unit,
    onEditAmmo: (AmmoProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = data.activeWeapon

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.section_rifles)) {
            data.weapons.forEach { w ->
                ProfileRow(
                    title = w.name,
                    subtitle = listOf(w.caliber, w.scopeName, data.ammoFor(w)?.name ?: stringResource(R.string.no_ammo))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    selected = w.id == active?.id,
                    onSelect = { onSelectWeapon(w.id) },
                    onEdit = { onEditWeapon(w) },
                )
            }
            TextButton(onClick = { onEditWeapon(WeaponProfile(ammoId = data.ammo.firstOrNull()?.id)) }) {
                Icon(Icons.Filled.Add, null)
                Text(stringResource(R.string.action_add_rifle))
            }
        }

        Section(stringResource(R.string.section_loads)) {
            data.ammo
                .sortedWith(compareBy({ it.diameterInOrMax() }, { it.caliberGroup() }, { it.name }))
                .groupBy { it.caliberGroup() }
                .forEach { (group, loads) ->
                    Text(
                        group.ifBlank { stringResource(R.string.caliber_unknown_group) },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    loads.forEach { a ->
                        ProfileRow(
                            title = a.name,
                            subtitle = "${a.dragType.name} ${a.bc} · ${a.massGr} gr · ${a.muzzleVelocity} m/s",
                            selected = null,
                            onSelect = { onEditAmmo(a) },
                            onEdit = { onEditAmmo(a) },
                        )
                    }
                }
            TextButton(onClick = { onEditAmmo(newLoadFor(active)) }) {
                Icon(Icons.Filled.Add, null)
                Text(stringResource(R.string.action_add_load))
            }
        }
    }
}

/** A new load starts with the cartridge and bullet diameter of the selected rifle, when it names a known cartridge. */
private fun newLoadFor(rifle: WeaponProfile?): AmmoProfile {
    val cartridge = rifle?.let { Cartridges.find(it.caliber) } ?: return AmmoProfile()
    return AmmoProfile(
        cartridge = cartridge.name,
        diameterMm = String.format(Locale.US, "%.2f", cartridge.bulletDiameterIn * 25.4),
    )
}

@Composable
private fun ProfileRow(title: String, subtitle: String, selected: Boolean?, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        if (selected != null) RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
    }
}
