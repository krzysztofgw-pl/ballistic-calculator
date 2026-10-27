package pl.ballistics.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import pl.ballistics.app.data.Cartridges

/** Free text with a list of known cartridges; cartridges for [diameterIn] are listed first. */
@Composable
fun CaliberField(label: String, value: String, diameterIn: Double?, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = diameterIn?.let { d -> Cartridges.all.sortedBy { if (Cartridges.sameDiameter(it.bulletDiameterIn, d)) 0 else 1 } }
        ?: Cartridges.all
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.action_choose_cartridge))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    onClick = { onChange(c.name); open = false },
                )
            }
        }
    }
}
