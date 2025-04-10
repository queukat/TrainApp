package com.queukat.train.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.queukat.train.R

/**
 *     (ExposedDropdownMenuBox).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDropdownMenu(
    stations: List<String>,
    selectedStation: String,
    onSelectStation: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedStation,
            onValueChange = { /* readOnly=true →    */ },
            readOnly = true,
            label = { Text(stringResource(R.string.hint_from_station)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor(
                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = true
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            stations.forEach { stationName ->
                DropdownMenuItem(
                    text = { StationDropdownItem(text = stationName) },
                    onClick = {
                        onSelectStation(stationName)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 *      .
 */
@Composable
fun StationDropdownItem(text: String) {
    Text(text = text)
}
