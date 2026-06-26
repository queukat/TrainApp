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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

private const val CALENDAR_MONTH_OFFSET = 1
private const val DATE_PART_COUNT = 3

data class SearchPanelState(
    val fromStation: String,
    val toStation: String,
    val selectedDate: String,
    val stops: List<StopEntity>,
    val language: String,
)

data class SearchPanelActions(
    val onFromChanged: (String) -> Unit,
    val onToChanged: (String) -> Unit,
    val onFromStopSelected: (StopEntity, String) -> Unit,
    val onToStopSelected: (StopEntity, String) -> Unit,
    val onDatePicked: (String) -> Unit,
    val onSearchClicked: () -> Unit,
)

@Composable
fun SearchPanel(
    state: SearchPanelState,
    actions: SearchPanelActions,
) {
    val context = LocalContext.current

    var fromField by remember { mutableStateOf(TextFieldValue(state.fromStation)) }
    var toField by remember { mutableStateOf(TextFieldValue(state.toStation)) }

    LaunchedEffect(state.stops.size, state.language) {
        Dbg.d(context, "SearchPanel", "stops.size=${state.stops.size} lang=${state.language}")
    }

    LaunchedEffect(state.fromStation) {
        if (state.fromStation != fromField.text) {
            fromField =
                TextFieldValue(
                    text = state.fromStation,
                    selection = TextRange(state.fromStation.length),
                )
        }
    }
    LaunchedEffect(state.toStation) {
        if (state.toStation != toField.text) {
            toField =
                TextFieldValue(
                    text = state.toStation,
                    selection = TextRange(state.toStation.length),
                )
        }
    }

    Column(Modifier.padding(8.dp)) {
        AutoCompleteTextField(
            value = fromField,
            onValueChange = { newValue ->
                fromField = newValue
                actions.onFromChanged(newValue.text)
            },
            stops = state.stops,
            onSuggestionSelected = { stop ->
                actions.onFromStopSelected(stop, stop.getNameForLanguage(state.language))
            },
            modifier = Modifier.fillMaxWidth(),
            options =
                AutoCompleteTextFieldOptions(
                    label = stringResource(R.string.hint_from_station),
                    language = state.language,
                    debugKey = "FROM",
                ),
        )

        Spacer(Modifier.height(6.dp))

        AutoCompleteTextField(
            value = toField,
            onValueChange = { newValue ->
                toField = newValue
                actions.onToChanged(newValue.text)
            },
            stops = state.stops,
            onSuggestionSelected = { stop ->
                actions.onToStopSelected(stop, stop.getNameForLanguage(state.language))
            },
            modifier = Modifier.fillMaxWidth(),
            options =
                AutoCompleteTextFieldOptions(
                    label = stringResource(R.string.hint_to_station),
                    language = state.language,
                    debugKey = "TO",
                ),
        )

        Spacer(Modifier.height(6.dp))
        DatePickerField(state.selectedDate, actions.onDatePicked)
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FilledIconButton(
                onClick = actions.onSearchClicked,
                modifier = Modifier.size(48.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
fun DatePickerField(
    dateString: String,
    onDatePicked: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    val calendar = Calendar.getInstance()
    val defaultDateString =
        String.format(
            locale,
            "%04d-%02d-%02d",
            calendar[Calendar.YEAR],
            calendar[Calendar.MONTH] + CALENDAR_MONTH_OFFSET,
            calendar[Calendar.DAY_OF_MONTH],
        )
    val displayedDate = dateString.ifBlank { defaultDateString }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp),
                ).background(CustomSurface)
                .clickable { showDatePicker = true }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayedDate,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Icon(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = stringResource(R.string.label_date),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }

    if (showDatePicker) {
        val initCal = Calendar.getInstance()
        val parts = dateString.split("-")
        if (parts.size == DATE_PART_COUNT) {
            val y = parts[0].toIntOrNull() ?: initCal[Calendar.YEAR]
            val m =
                (parts[1].toIntOrNull() ?: (initCal[Calendar.MONTH] + CALENDAR_MONTH_OFFSET)) -
                    CALENDAR_MONTH_OFFSET
            val d = parts[2].toIntOrNull() ?: initCal[Calendar.DAY_OF_MONTH]
            initCal[Calendar.YEAR] = y
            initCal[Calendar.MONTH] = m
            initCal[Calendar.DAY_OF_MONTH] = d
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val realMonth = month + CALENDAR_MONTH_OFFSET
                val newDateString =
                    String.format(
                        locale,
                        "%04d-%02d-%02d",
                        year,
                        realMonth,
                        dayOfMonth,
                    )
                onDatePicked(newDateString)
                showDatePicker = false
            },
            initCal[Calendar.YEAR],
            initCal[Calendar.MONTH],
            initCal[Calendar.DAY_OF_MONTH],
        ).show()
    }
}
