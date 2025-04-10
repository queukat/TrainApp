package com.queukat.train.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 *    /  ё .
 */
@Composable
fun SavedRoutesBlock(
    fromStation: String,
    toStation: String,
    savedRoutes: List<String>,
    onSelectRoute: (String) -> Unit,
    onSaveRoute: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

     val currentRouteString = when {
        fromStation.isNotBlank() && toStation.isNotBlank() -> {
            "$fromStation - $toStation"
        }
        savedRoutes.isNotEmpty() -> {
            savedRoutes.first()
        }
        else -> {
            "No saved routes"
        }
    }



    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        //  "Save route"
        Button(onClick = onSaveRoute) {
            Text("Save route")
        }

        //      (▼).    .
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(12.dp)
            ) {
                //   
                Text(currentRouteString)

                //  ""
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                    contentDescription = "Expand saved routes"
                )
            }

            //  
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                //   ё 
                savedRoutes.forEach { route ->
                    DropdownMenuItem(
                        text = { Text(route) },
                        onClick = {
                            expanded = false
                            //  ,    route
                            onSelectRoute(route)
                        }
                    )
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun SavedRoutesBlockPreview() {
    //     
    var fromStation by remember { mutableStateOf("Podgorica") }
    var toStation by remember { mutableStateOf("Bar") }

    val savedRoutes = listOf(
        "Bar - Podgorica",
        "Podgorica - Bijelo Polje",
        "Sutomore - Bar",
        "Nikšić - Podgorica"
    )

    //           ,
    //    "Bar - Podgorica"   fromStation/toStation
    SavedRoutesBlock(
        fromStation = fromStation,
        toStation = toStation,
        savedRoutes = savedRoutes,
        onSelectRoute = { routeStr ->
            val parts = routeStr.split(" - ")
            if (parts.size == 2) {
                fromStation = parts[0]
                toStation = parts[1]
            }
        },
        onSaveRoute = {
            //     
        }
    )
}
