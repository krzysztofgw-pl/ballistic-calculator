package pl.ballistics.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.ballistics.app.R
import pl.ballistics.app.data.SavedTarget

/**
 * The range card as sub-tabs: free entry, each saved target, and one to save the current values
 * as a new target. Tapping the open target renames or deletes it.
 */
@Composable
fun TargetTabs(
    targets: List<SavedTarget>,
    activeId: String?,
    onSelect: (String?) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<SavedTarget?>(null) }
    val selected = targets.indexOfFirst { it.id == activeId }.let { if (it < 0) 0 else it + 1 }
    val newName = stringResource(R.string.target_default_name, targets.size + 1)

    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
        Tab(selected = selected == 0, onClick = { onSelect(null) }, text = { Text(stringResource(R.string.target_free)) })
        targets.forEach { t ->
            Tab(
                selected = t.id == activeId,
                onClick = { if (t.id == activeId) editing = t else onSelect(t.id) },
                text = { Text(t.name.ifBlank { newName }) },
            )
        }
        Tab(
            selected = false,
            onClick = { onAdd(newName) },
            icon = { Icon(Icons.Filled.Add, stringResource(R.string.action_add_target)) },
        )
    }

    editing?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.title_target)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { onRename(t.id, name.trim()); editing = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { onDelete(t.id); editing = null }) { Text(stringResource(R.string.action_delete)) } },
        )
    }
}
