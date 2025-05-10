package com.queukat.train.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage

/**
 *     .
 * [text] –   
 * [onTextChange] –   
 * [stops] –    ( VM)
 * [language] – "en" / "ru" / "me" / ...
 */
@Composable
fun AutoCompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    stops: List<StopEntity>,
    modifier: Modifier = Modifier,
    label: String = "Station",
    language: String = "en"
) {
    var expanded   by remember { mutableStateOf(false) }
    val focusState = remember { mutableStateOf(false) }
    val keyboard   = LocalSoftwareKeyboardController.current

    val filteredStops = remember(value.text, stops) {
        if (value.text.isBlank()) emptyList()
        else stops.filter {
            it.getNameForLanguage(language)
                .contains(value.text, ignoreCase = true)
        }
    }

    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newVal ->
                onValueChange(newVal)
                expanded = focusState.value && newVal.text.isNotBlank()
            },
            label      = { Text(label) },
            singleLine = true,
            modifier   = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs ->
                    focusState.value = fs.isFocused
                    expanded = fs.isFocused && value.text.isNotBlank()
                }
        )

        DropdownMenu(
            expanded          = expanded && filteredStops.isNotEmpty(),
            onDismissRequest  = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            properties = PopupProperties(focusable = false)  // ← вот тут
        ) {
            filteredStops.forEach { stop ->
                val stationName = stop.getNameForLanguage(language)
                DropdownMenuItem(
                    text = { Text(stationName) },
                    onClick = {
                        onValueChange(
                            TextFieldValue(
                                text      = stationName,
                                selection = TextRange(stationName.length)
                            )
                        )
                        expanded = false
                        keyboard?.hide()  // клаву убираем только после выбора
                    }
                )
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun AutoCompleteTextFieldPreview() {
    val sampleStops = listOf(
        StopEntity(1, "Podgorica", "Podgorica", "", 1, 0.0, 0.0, 1),
        StopEntity(2, "Bar",        "Bar",        "", 1, 0.0, 0.0, 1),
        StopEntity(3, "Sutomore",   "Sutomore",   "", 1, 0.0, 0.0, 1),
    )
    var tfState by remember { mutableStateOf(TextFieldValue()) }

    AutoCompleteTextField(
        value = tfState,
        onValueChange = { tfState = it },
        stops = sampleStops,
        label = "Station",
        language = "en",
        modifier = Modifier.fillMaxWidth()
    )
}
