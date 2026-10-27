package pl.ballistics.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.ballistics.app.MainViewModel
import pl.ballistics.app.R
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.WeaponProfile

private data class MainTab(@StringRes val title: Int, val icon: ImageVector)

private val tabs = listOf(
    MainTab(R.string.tab_profiles, Icons.Filled.Person),
    MainTab(R.string.tab_conditions, Icons.Filled.Place),
    MainTab(R.string.tab_table, Icons.AutoMirrored.Filled.List),
    MainTab(R.string.tab_target, Icons.Filled.Search),
)

/** What is open on top of the tabs. */
private sealed interface Editor {
    data class Weapon(val profile: WeaponProfile) : Editor
    data class Ammo(val profile: AmmoProfile) : Editor
    data object Settings : Editor
}

private const val TARGET_TAB = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
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
                weapons = data.weapons,
                isNew = data.ammo.none { it.id == e.profile.id },
                onSave = { vm.saveAmmo(it); editor = null },
                onDelete = { vm.deleteAmmo(e.profile.id); editor = null },
                onClose = { editor = null },
            )
            return
        }
        Editor.Settings -> {
            BackHandler { editor = null }
            SettingsScreen(
                ui = data.ui,
                onUi = vm::updateUi,
                sync = sync,
                onSignIn = vm::signIn,
                onSignOut = vm::signOut,
                onExport = vm::exportData,
                onImport = vm::importData,
                onClose = { editor = null },
            )
            return
        }
        null -> Unit
    }

    val view = LocalView.current
    DisposableEffect(data.ui.keepScreenOn) {
        view.keepScreenOn = data.ui.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val targetTabs: @Composable () -> Unit = {
        TargetTabs(
            targets = data.targets,
            activeId = data.activeTargetId,
            onSelect = vm::selectTarget,
            onAdd = vm::addTarget,
            onRename = vm::renameTarget,
            onDelete = vm::deleteTarget,
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(tabs[tab].title)) },
                    actions = {
                        IconButton(onClick = { editor = Editor.Settings }) {
                            Icon(Icons.Filled.Settings, stringResource(R.string.title_settings))
                        }
                    },
                )
                if (data.ui.tabsAtTop) {
                    TabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { index, t ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(stringResource(t.title)) },
                                icon = { Icon(t.icon, contentDescription = null) },
                            )
                        }
                    }
                }
                if (tab == TARGET_TAB && data.ui.subTabsAtTop) targetTabs()
            }
        },
        bottomBar = {
            Column {
                // Sub-tabs sit right above the main tabs, within reach of the thumb.
                if (tab == TARGET_TAB && !data.ui.subTabsAtTop) targetTabs()
                if (!data.ui.tabsAtTop) {
                    NavigationBar {
                        tabs.forEachIndexed { index, t ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.title)) },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
                modifier = modifier,
            )
            1 -> ConditionsScreen(data.conditions, vm::updateConditions, modifier)
            2 -> TableScreen(result, modifier)
            else -> TargetScreen(result, data.conditions, vm::updateConditions, modifier)
        }
    }
}
