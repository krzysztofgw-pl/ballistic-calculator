package pl.ballistics.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.ballistics.app.MainViewModel

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Broń i amunicja", Icons.Filled.Build),
    Tab("Warunki", Icons.Filled.Place),
    Tab("Tabela", Icons.AutoMirrored.Filled.List),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(2) }

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
            0 -> RifleScreen(inputs, vm::update, modifier)
            1 -> ConditionsScreen(inputs, vm::update, modifier)
            else -> TableScreen(result, modifier)
        }
    }
}
