package com.queukat.train.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    text: String,
    onTextChange: (String) -> Unit,
    stops: List<StopEntity>,
    modifier: Modifier = Modifier,
    label: String = "Station",
    language: String = "en"
) {
    var expanded by remember { mutableStateOf(false) }

    val filteredStops = remember(text, stops) {
        if (text.isBlank()) emptyList()
        else stops.filter {
            val stationName = it.getNameForLanguage(language)
            stationName.contains(text, ignoreCase = true)
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                onTextChange(it)
                expanded = true  //     
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true
        )

        //    5    
        val visibleStops = filteredStops

        DropdownMenu(
            expanded = expanded && visibleStops.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            properties = PopupProperties(focusable = false)
        ) {
            visibleStops.forEach { stop ->
                val stationName = stop.getNameForLanguage(language)
                DropdownMenuItem(
                    text = { Text(stationName) },
                    onClick = {
                        onTextChange(stationName)
                        expanded = false
                    }
                )
            }
        }
    }
}


/**
 *   AutoCompleteTextField.
 */
@Preview(showBackground = true)
@Composable
fun AutoCompleteTextFieldPreview() {
    //   
    val sampleStops = listOf(
        StopEntity(
            stopId = 1,
            nameEn = "Podgorica",
            nameMe = "Podgorica",
            nameMeCyr = "",
            stopTypeId = 1,
            latitude = 42.4417,
            longitude = 19.2636,
            local = 1
        ),
        StopEntity(
            stopId = 2,
            nameEn = "Bar",
            nameMe = "Bar",
            nameMeCyr = "",
            stopTypeId = 1,
            latitude = 42.0930,
            longitude = 19.1002,
            local = 1
        ),
        StopEntity(
            stopId = 3,
            nameEn = "Sutomore",
            nameMe = "Sutomore",
            nameMeCyr = "",
            stopTypeId = 1,
            latitude = 42.1423,
            longitude = 19.0462,
            local = 1
        ),
        StopEntity(
            stopId = 4,
            nameEn = "Kolašin",
            nameMe = "Kolašin",
            nameMeCyr = "",
            stopTypeId = 1,
            latitude = 42.8238,
            longitude = 19.5169,
            local = 1
        ),
        StopEntity(
            stopId = 5,
            nameEn = "Nikšić",
            nameMe = "Nikšić",
            nameMeCyr = "ћ",
            stopTypeId = 1,
            latitude = 42.7769,
            longitude = 18.9461,
            local = 1
        ),
        StopEntity(
            stopId = 6,
            nameEn = "Bijelo Polje",
            nameMe = "Bijelo Polje",
            nameMeCyr = "ј љ",
            stopTypeId = 1,
            latitude = 43.0393,
            longitude = 19.7452,
            local = 1
        )
    )

    //   –  ,    ё 
    var inputText by remember { mutableStateOf("") }

    AutoCompleteTextField(
        text = inputText,
        onTextChange = { inputText = it },
        stops = sampleStops,
        label = "Station (Preview)",
        language = "en"
    )
}