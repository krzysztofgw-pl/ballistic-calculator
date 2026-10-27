package pl.ballistics.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.ballistics.app.MainViewModel
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.WeaponProfile

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Profile", Icons.Filled.Person),
    Tab("Warunki", Icons.Filled.Place),
    Tab("Tabela", Icons.AutoMirrored.Filled.List),
)

/** What is open on top of the tabs. */
private sealed interface Editor {
    data class Weapon(val profile: WeaponProfile) : Editor
    data class Ammo(val profile: AmmoProfile) : Editor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(2) }
    var editor by remember { mutableStateOf<Editor?>(null) }

    when (val e = editor) {
        is Editor.Weapon -> {
            BackHandler { editor = null }
            WeaponEditor(
                initial = e.profile,
                ammo = data.ammo,
                isNew = data.weapons.none { it.id == e.profile.id },
                onSave = { vm.saveWeapon(it); editor = null },
                onDelete = { vm.deleteWeapon(e.profile.id); editor = null },
                onClose = { editor = null },
            )
            return
        }
        is Editor.Ammo -> {
            BackHandler { editor = null }
            AmmoEditor(
                initial = e.profile,
                isNew = data.ammo.none { it.id == e.profile.id },
                onSave = { vm.saveAmmo(it); editor = null },
                onDelete = { vm.deleteAmmo(e.profile.id); editor = null },
                onClose = { editor = null },
            )
            return
        }
        null -> Unit
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[tab].title) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> ProfilesScreen(
                data = data,
                onSelectWeapon = vm::selectWeapon,
                onEditWeapon = { editor = Editor.Weapon(it) },
                onEditAmmo = { editor = Editor.Ammo(it) },
                onExport = vm::exportData,
                onImport = vm::importData,
                modifier = modifier,
            )
            1 -> ConditionsScreen(data.conditions, vm::updateConditions, modifier)
            else -> TableScreen(result, modifier)
        }
    }
}
