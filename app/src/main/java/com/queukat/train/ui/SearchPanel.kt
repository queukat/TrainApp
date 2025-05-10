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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.db.StopEntity
import com.queukat.train.ui.theme.CustomSurface
import com.queukat.train.ui.theme.TrainAppTheme
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.text.input.TextFieldValue


@Composable
fun SearchPanel(
    fromStation: String,
    toStation:   String,
    selectedDate: String,
    stops: List<StopEntity>,
    language: String,
    onFromChanged: (String) -> Unit,
    onToChanged:   (String) -> Unit,
    onDatePicked:  (String) -> Unit,
    onSearchClicked: () -> Unit
) {
    // 1) локальные состояния TextFieldValue
    var fromField by remember { mutableStateOf(TextFieldValue(fromStation)) }
    var toField   by remember { mutableStateOf(TextFieldValue(toStation)) }

    /* если снаружи пришло новое значение – подстроимся */
    LaunchedEffect(fromStation) {
        if (fromStation != fromField.text) {
            fromField = TextFieldValue(
                text = fromStation,
                selection = TextRange(fromStation.length)   // курсор в конец
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

        /* -------- From -------- */
        AutoCompleteTextField(
            value = fromField,
            onValueChange = { newValue ->
                fromField = newValue           // обновляем поле
                onFromChanged(newValue.text)   // наружу — строку
            },
            stops = stops,
            label = stringResource(R.string.hint_from_station),
            language = language,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        /* -------- To -------- */
        AutoCompleteTextField(
            value = toField,
            onValueChange = { newValue ->
                toField = newValue
                onToChanged(newValue.text)
            },
            stops = stops,
            label = stringResource(R.string.hint_to_station),
            language = language,
            modifier = Modifier.fillMaxWidth()
        )

        /* -------- дата, кнопка поиска — остаётся без изменений -------- */
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


/**
 * «Фейковое» поле для выбора даты: при нажатии — DatePickerDialog.
 * Убрали «label» сверху (как вы просили), отображаем только дату и иконку.
 */
@Composable
fun DatePickerField(
    dateString: String,
    onDatePicked: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Если dateString пустое, используем «сегодня»
    val calendar = Calendar.getInstance()
    val defaultDateString = String.format(
        Locale.getDefault(),
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val displayedDate = dateString.ifBlank { defaultDateString }

    // Само «поле»
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
        // Текст даты
        Text(
            text = displayedDate,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Иконка
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = stringResource(R.string.label_date),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }

    // Показать DatePickerDialog
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

@Composable
@Preview(name = "SearchPanel Light Theme", showBackground = true)
fun SearchPanelLightPreview() {
    TrainAppTheme(darkTheme = false) {
        var fromStation by remember { mutableStateOf("Podgorica") }
        var toStation by remember { mutableStateOf("Bar") }
        var date by remember { mutableStateOf("2025-12-31") }

        val dummyStops = listOf(
            StopEntity(
                stopId = 1,
                nameEn = "Podgorica",
                nameMe = "Podgorica",
                nameMeCyr = "Подгорица",
                stopTypeId = 1,
                latitude = 42.4417,
                longitude = 19.2636,
                local = 1
            ),
            StopEntity(
                stopId = 2,
                nameEn = "Bar",
                nameMe = "Bar",
                nameMeCyr = "Бар",
                stopTypeId = 1,
                latitude = 42.0930,
                longitude = 19.1002,
                local = 1
            )
        )

        SearchPanel(
            fromStation = fromStation,
            toStation = toStation,
            selectedDate = date,
            stops = dummyStops,
            language = "en",
            onFromChanged = { fromStation = it },
            onToChanged = { toStation = it },
            onDatePicked = { date = it },
            onSearchClicked = {}
        )
    }
}

@Composable
@Preview(name = "SearchPanel Dark Theme", showBackground = true)
fun SearchPanelDarkPreview() {
    TrainAppTheme(darkTheme = true) {
        var fromStation by remember { mutableStateOf("Podgorica") }
        var toStation by remember { mutableStateOf("Bar") }
        var date by remember { mutableStateOf("2025-12-31") }

        val dummyStops = listOf(
            StopEntity(
                stopId = 1,
                nameEn = "Podgorica",
                nameMe = "Podgorica",
                nameMeCyr = "Подгорица",
                stopTypeId = 1,
                latitude = 42.4417,
                longitude = 19.2636,
                local = 1
            ),
            StopEntity(
                stopId = 2,
                nameEn = "Bar",
                nameMe = "Bar",
                nameMeCyr = "Бар",
                stopTypeId = 1,
                latitude = 42.0930,
                longitude = 19.1002,
                local = 1
            )
        )

        SearchPanel(
            fromStation = fromStation,
            toStation = toStation,
            selectedDate = date,
            stops = dummyStops,
            language = "en",
            onFromChanged = { fromStation = it },
            onToChanged = { toStation = it },
            onDatePicked = { date = it },
            onSearchClicked = {}
        )
    }
}

