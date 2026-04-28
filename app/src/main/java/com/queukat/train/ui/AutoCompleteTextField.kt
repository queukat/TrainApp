package com.queukat.train.ui

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
import androidx.compose.runtime.*
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
import com.queukat.train.util.Dbg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AutoCompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    stops: List<StopEntity>,
    modifier: Modifier = Modifier,
    onSuggestionSelected: (StopEntity) -> Unit = {},
    label: String = "Station",
    language: String = "en",
    debugKey: String = label
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dbgEnabled = remember { Dbg.isEnabled(context) }

    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<StopEntity>>(emptyList()) }

    // LOCK: после клика по подсказке IME может прислать “старое” значение и затереть выбранное.
    var lockedText by remember { mutableStateOf<String?>(null) }
    var lockUntilMs by remember { mutableLongStateOf(0L) }

    fun lockSelection(text: String) {
        lockedText = text
        lockUntilMs = System.currentTimeMillis() + 500L
        if (dbgEnabled) Dbg.d(context, "AC/$debugKey", "LOCK '$text' for 500ms")
    }

    fun clearLock(reason: String) {
        if (lockedText != null && dbgEnabled) Dbg.d(context, "AC/$debugKey", "UNLOCK ($reason)")
        lockedText = null
        lockUntilMs = 0L
    }

    LaunchedEffect(focused) {
        if (dbgEnabled) Dbg.d(context, "AC/$debugKey", "focused=$focused stops=${stops.size} lang=$language")
        if (!focused) clearLock("focus lost")
    }

    LaunchedEffect(value.text, stops, language, focused) {
        val text = value.text

        if (!focused || text.isBlank() || stops.isEmpty()) {
            suggestions = emptyList()
            if (dbgEnabled) Dbg.d(context, "AC/$debugKey", "skip filter: focused=$focused text='${text.take(30)}' stops=${stops.size}")
            return@LaunchedEffect
        }

        delay(80)

        val query = text.trim()
        val result = withContext(Dispatchers.Default) {
            stops.asSequence()
                .filter { stop -> matchesAnyLanguage(stop, language, query) }
                .take(30)
                .toList()
        }

        suggestions = result

        if (dbgEnabled) {
            Dbg.d(context, "AC/$debugKey", "filter done: query='${query.take(30)}' suggestions=${result.size} stops=${stops.size}")
            if (result.isNotEmpty()) Dbg.d(context, "AC/$debugKey", "example(show): '${result.first().getNameForLanguage(language)}'")
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newVal ->
                val now = System.currentTimeMillis()
                val lt = lockedText

                // Если lock активен — пропускаем только то, что равно выбранному тексту
                if (lt != null && now <= lockUntilMs) {
                    if (newVal.text != lt) {
                        if (dbgEnabled) {
                            Dbg.d(context, "AC/$debugKey", "IGNORED IME change '${newVal.text.take(30)}' (locked='$lt')")
                        }
                        return@OutlinedTextField
                    } else {
                        clearLock("got locked text from IME")
                    }
                } else if (lt != null && now > lockUntilMs) {
                    clearLock("lock expired")
                }

                onValueChange(newVal)
                if (dbgEnabled) Dbg.d(context, "AC/$debugKey", "onValueChange: '${newVal.text.take(30)}'")
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs ->
                    focused = fs.isFocused
                    if (!focused) suggestions = emptyList()
                }
        )

        if (focused && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 260.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(suggestions) { stop ->
                        val stationName = stop.getNameForLanguage(language)
                        Text(
                            text = stationName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (dbgEnabled) Dbg.d(context, "AC/$debugKey", "pick: '$stationName'")

                                    // 1) Ставим lock
                                    lockSelection(stationName)

                                    // 2) Пушим выбранное значение
                                    onValueChange(
                                        TextFieldValue(
                                            text = stationName,
                                            selection = TextRange(stationName.length)
                                        )
                                    )
                                    onSuggestionSelected(stop)

                                    // 3) Закрываем подсказки и убираем IME
                                    suggestions = emptyList()
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun matchesAnyLanguage(stop: StopEntity, displayLang: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false

    val displayName = stop.getNameForLanguage(displayLang)
    if (displayName.contains(q, ignoreCase = true)) return true

    if (stop.nameEn.contains(q, ignoreCase = true)) return true
    if (stop.nameMe.contains(q, ignoreCase = true)) return true
    val cyr = stop.nameMeCyr
    if (!cyr.isNullOrBlank() && cyr.contains(q, ignoreCase = true)) return true

    return false
}
