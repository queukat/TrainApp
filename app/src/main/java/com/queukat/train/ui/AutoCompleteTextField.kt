package com.queukat.train.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.queukat.train.data.db.StopEntity
import com.queukat.train.data.db.getNameForLanguage
import com.queukat.train.data.db.isPassengerSearchStop
import com.queukat.train.util.AppDispatchers
import com.queukat.train.util.Dbg
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SELECTION_LOCK_MS = 500L
private const val DEBUG_TEXT_PREVIEW_LENGTH = 30
private const val SUGGESTION_FILTER_DELAY_MS = 80L
private const val MAX_SUGGESTIONS = 30
private val suggestionsMaxHeight = 260.dp

data class AutoCompleteTextFieldOptions(
    val label: String = "Station",
    val language: String = "en",
    val debugKey: String = label,
)

@Composable
fun AutoCompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    stops: List<StopEntity>,
    modifier: Modifier = Modifier,
    onSuggestionSelected: (StopEntity) -> Unit = {},
    options: AutoCompleteTextFieldOptions = AutoCompleteTextFieldOptions(),
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dbgEnabled = remember { Dbg.isEnabled(context) }

    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<StopEntity>>(emptyList()) }
    val selectionLock = remember { SelectionLockState() }

    AutoCompleteFocusEffect(focused, stops.size, options, context, dbgEnabled, selectionLock)
    AutoCompleteSuggestionsEffect(
        value = value,
        stops = stops,
        focused = focused,
        options = options,
        context = context,
        dbgEnabled = dbgEnabled,
        onSuggestionsChanged = { suggestions = it },
    )

    Column(modifier = modifier) {
        AutoCompleteInputField(
            value = value,
            options = options,
            context = context,
            dbgEnabled = dbgEnabled,
            selectionLock = selectionLock,
            onValueChange = onValueChange,
            onFocusedChanged = { isFocused ->
                focused = isFocused
                if (!isFocused) suggestions = emptyList()
            },
        )

        if (focused && suggestions.isNotEmpty()) {
            AutoCompleteSuggestionsCard(
                suggestions = suggestions,
                options = options,
                context = context,
                dbgEnabled = dbgEnabled,
                onSuggestionSelected = { stop, stationName ->
                    selectionLock.lockSelection(stationName, context, options, dbgEnabled)
                    onValueChange(TextFieldValue(text = stationName, selection = TextRange(stationName.length)))
                    onSuggestionSelected(stop)
                    suggestions = emptyList()
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
            )
        }
    }
}

@Composable
private fun AutoCompleteFocusEffect(
    focused: Boolean,
    stopCount: Int,
    options: AutoCompleteTextFieldOptions,
    context: Context,
    dbgEnabled: Boolean,
    selectionLock: SelectionLockState,
) {
    LaunchedEffect(focused) {
        logAutocomplete(context, options, dbgEnabled, "focused=$focused stops=$stopCount lang=${options.language}")
        if (!focused) selectionLock.clear("focus lost", context, options, dbgEnabled)
    }
}

@Composable
private fun AutoCompleteSuggestionsEffect(
    value: TextFieldValue,
    stops: List<StopEntity>,
    focused: Boolean,
    options: AutoCompleteTextFieldOptions,
    context: Context,
    dbgEnabled: Boolean,
    onSuggestionsChanged: (List<StopEntity>) -> Unit,
) {
    LaunchedEffect(value.text, stops, options.language, focused) {
        val text = value.text
        if (!canFilterSuggestions(focused, text, stops)) {
            onSuggestionsChanged(emptyList())
            logAutocomplete(
                context,
                options,
                dbgEnabled,
                "skip filter: focused=$focused text='${text.take(DEBUG_TEXT_PREVIEW_LENGTH)}' stops=${stops.size}",
            )
            return@LaunchedEffect
        }

        delay(SUGGESTION_FILTER_DELAY_MS)
        val query = text.trim()
        val result = filterSuggestions(stops, options.language, query)
        onSuggestionsChanged(result)
        logSuggestionsResult(context, options, dbgEnabled, query, result, stops.size)
    }
}

@Composable
private fun AutoCompleteInputField(
    value: TextFieldValue,
    options: AutoCompleteTextFieldOptions,
    context: Context,
    dbgEnabled: Boolean,
    selectionLock: SelectionLockState,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusedChanged: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            if (selectionLock.accepts(newVal.text, context, options, dbgEnabled)) {
                onValueChange(newVal)
                logAutocomplete(
                    context,
                    options,
                    dbgEnabled,
                    "onValueChange: '${newVal.text.take(DEBUG_TEXT_PREVIEW_LENGTH)}'",
                )
            }
        },
        label = { Text(options.label) },
        singleLine = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { fs -> onFocusedChanged(fs.isFocused) },
    )
}

@Composable
private fun AutoCompleteSuggestionsCard(
    suggestions: List<StopEntity>,
    options: AutoCompleteTextFieldOptions,
    context: Context,
    dbgEnabled: Boolean,
    onSuggestionSelected: (StopEntity, String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(max = suggestionsMaxHeight),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(suggestions) { stop ->
                val stationName = stop.getNameForLanguage(options.language)
                Text(
                    text = stationName,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                logAutocomplete(context, options, dbgEnabled, "pick: '$stationName'")
                                onSuggestionSelected(stop, stationName)
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private class SelectionLockState {
    private var lockedText by mutableStateOf<String?>(null)
    private var lockUntilMs by mutableLongStateOf(0L)

    fun lockSelection(
        text: String,
        context: Context,
        options: AutoCompleteTextFieldOptions,
        dbgEnabled: Boolean,
    ) {
        lockedText = text
        lockUntilMs = System.currentTimeMillis() + SELECTION_LOCK_MS
        logAutocomplete(context, options, dbgEnabled, "LOCK '$text' for ${SELECTION_LOCK_MS}ms")
    }

    fun clear(
        reason: String,
        context: Context,
        options: AutoCompleteTextFieldOptions,
        dbgEnabled: Boolean,
    ) {
        if (lockedText != null) {
            logAutocomplete(context, options, dbgEnabled, "UNLOCK ($reason)")
        }
        lockedText = null
        lockUntilMs = 0L
    }

    fun accepts(
        newText: String,
        context: Context,
        options: AutoCompleteTextFieldOptions,
        dbgEnabled: Boolean,
    ): Boolean {
        val lock = lockedText ?: return true
        val now = System.currentTimeMillis()

        if (now > lockUntilMs) {
            clear("lock expired", context, options, dbgEnabled)
            return true
        }
        if (newText == lock) {
            clear("got locked text from IME", context, options, dbgEnabled)
            return true
        }

        logAutocomplete(
            context,
            options,
            dbgEnabled,
            "IGNORED IME change '${newText.take(DEBUG_TEXT_PREVIEW_LENGTH)}' (locked='$lock')",
        )
        return false
    }
}

private suspend fun filterSuggestions(
    stops: List<StopEntity>,
    language: String,
    query: String,
): List<StopEntity> =
    withContext(AppDispatchers.Default) {
        stops
            .asSequence()
            .filter { stop -> stop.isPassengerSearchStop() }
            .filter { stop -> matchesAnyLanguage(stop, language, query) }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

private fun canFilterSuggestions(
    focused: Boolean,
    text: String,
    stops: List<StopEntity>,
): Boolean = focused && text.isNotBlank() && stops.isNotEmpty()

private fun logSuggestionsResult(
    context: Context,
    options: AutoCompleteTextFieldOptions,
    dbgEnabled: Boolean,
    query: String,
    result: List<StopEntity>,
    stopCount: Int,
) {
    logAutocomplete(
        context,
        options,
        dbgEnabled,
        "filter done: query='${query.take(DEBUG_TEXT_PREVIEW_LENGTH)}' suggestions=${result.size} stops=$stopCount",
    )
    result.firstOrNull()?.let { stop ->
        logAutocomplete(context, options, dbgEnabled, "example(show): '${stop.getNameForLanguage(options.language)}'")
    }
}

private fun logAutocomplete(
    context: Context,
    options: AutoCompleteTextFieldOptions,
    enabled: Boolean,
    message: String,
) {
    if (enabled) {
        Dbg.d(context, "AC/${options.debugKey}", message)
    }
}

private fun matchesAnyLanguage(
    stop: StopEntity,
    displayLang: String,
    query: String,
): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false

    val displayName = stop.getNameForLanguage(displayLang)
    val searchableNames =
        listOfNotNull(
            displayName,
            stop.nameEn,
            stop.nameMe,
            stop.nameMeCyr,
        )
    return searchableNames.any { name -> name.contains(q, ignoreCase = true) }
}
