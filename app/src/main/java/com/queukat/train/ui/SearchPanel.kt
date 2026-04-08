package com.queukat.train.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.ui.theme.CustomSurface
import com.queukat.train.util.Dbg
import java.util.Calendar
import java.util.Locale

@Composable
fun SearchPanel(
    fromStation: String,
    toStation: String,
    selectedDate: String,
    stops: List<StopEntity>,
    language: String,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
    onFromStopSelected: (StopEntity, String) -> Unit,
    onToStopSelected: (StopEntity, String) -> Unit,
    onDatePicked: (String) -> Unit,
    onSearchClicked: () -> Unit
) {
    val context = LocalContext.current
    val dbgEnabled = remember { Dbg.isEnabled(context) }

    var fromField by remember { mutableStateOf(TextFieldValue(fromStation)) }
    var toField by remember { mutableStateOf(TextFieldValue(toStation)) }

    LaunchedEffect(stops.size, language) {
        Dbg.d(context, "SearchPanel", "stops.size=${stops.size} lang=$language")
    }

    LaunchedEffect(fromStation) {
        if (fromStation != fromField.text) {
            fromField = TextFieldValue(
                text = fromStation,
                selection = TextRange(fromStation.length)
            )
        }
    }
    LaunchedEffect(toStation) {
        if (toStation != toField.text) {
            toField = TextFieldValue(
                text = toStation,
                selection = TextRange(toStation.length)
            )
        }
    }

    Column(Modifier.padding(8.dp)) {
        AutoCompleteTextField(
            value = fromField,
            onValueChange = { newValue ->
                fromField = newValue
                onFromChanged(newValue.text)
            },
            stops = stops,
            onSuggestionSelected = { stop ->
                onFromStopSelected(stop, stop.getNameForLanguage(language))
            },
            label = stringResource(R.string.hint_from_station),
            language = language,
            modifier = Modifier.fillMaxWidth(),
            debugKey = "FROM"
        )

        Spacer(Modifier.height(6.dp))

        AutoCompleteTextField(
            value = toField,
            onValueChange = { newValue ->
                toField = newValue
                onToChanged(newValue.text)
            },
            stops = stops,
            onSuggestionSelected = { stop ->
                onToStopSelected(stop, stop.getNameForLanguage(language))
            },
            label = stringResource(R.string.hint_to_station),
            language = language,
            modifier = Modifier.fillMaxWidth(),
            debugKey = "TO"
        )

        Spacer(Modifier.height(6.dp))
        DatePickerField(selectedDate, onDatePicked)
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = onSearchClicked,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun DatePickerField(
    dateString: String,
    onDatePicked: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val calendar = Calendar.getInstance()
    val defaultDateString = String.format(
        Locale.getDefault(),
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val displayedDate = dateString.ifBlank { defaultDateString }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            )
            .background(CustomSurface)
            .clickable { showDatePicker = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayedDate,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = stringResource(R.string.label_date),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }

    if (showDatePicker) {
        val initCal = Calendar.getInstance()
        val parts = dateString.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull() ?: initCal.get(Calendar.YEAR)
            val m = (parts[1].toIntOrNull() ?: (initCal.get(Calendar.MONTH) + 1)) - 1
            val d = parts[2].toIntOrNull() ?: initCal.get(Calendar.DAY_OF_MONTH)
            initCal.set(y, m, d)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val realMonth = month + 1
                val newDateString = String.format(
                    Locale.getDefault(),
                    "%04d-%02d-%02d",
                    year,
                    realMonth,
                    dayOfMonth
                )
                onDatePicked(newDateString)
                showDatePicker = false
            },
            initCal.get(Calendar.YEAR),
            initCal.get(Calendar.MONTH),
            initCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
