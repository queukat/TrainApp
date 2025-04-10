package com.queukat.train.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.queukat.train.R
import com.queukat.train.ui.theme.TrainAppTheme



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    languages: List<String>,
    reminderOptions: List<String>,
    initialLanguage: String,
    initialReminder: String,
    initialMinutes: Int,
    initialAutoRefresh: Boolean,
    onApply: (chosenLang: String, chosenReminder: String, minutes: Int, autoRefresh: Boolean) -> Unit,
    onTestPushNow: () -> Unit,
    onTestPushLater: () -> Unit,
    onBackClick: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var selectedReminder by remember { mutableStateOf(initialReminder) }
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }
    var autoRefreshChecked by remember { mutableStateOf(initialAutoRefresh) }

    var langMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.btn_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                modifier = Modifier.height(74.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(text = stringResource(R.string.label_select_language_for_stations))

            ExposedDropdownMenuBox(
                expanded = langMenuExpanded,
                onExpandedChange = { langMenuExpanded = !langMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_select_language_for_stations)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                )
                ExposedDropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                selectedLanguage = lang
                                langMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hint_language_for_stations),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.label_default_reminder))

            ExposedDropdownMenuBox(
                expanded = reminderMenuExpanded,
                onExpandedChange = { reminderMenuExpanded = !reminderMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedReminder,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.dialog_reminder_title)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                )
                ExposedDropdownMenu(
                    expanded = reminderMenuExpanded,
                    onDismissRequest = { reminderMenuExpanded = false }
                ) {
                    reminderOptions.forEach { rem ->
                        DropdownMenuItem(
                            text = { Text(rem) },
                            onClick = {
                                selectedReminder = rem
                                reminderMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.label_minutes_before))
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = autoRefreshChecked,
                    onCheckedChange = { autoRefreshChecked = it }
                )
                Text(text = stringResource(R.string.label_auto_refresh))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTestPushNow,
                    modifier = Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_test_push_now))
                }
                Button(
                    onClick = onTestPushLater,
                    modifier = Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_test_push_later))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val mins = minutesText.toIntOrNull() ?: 15
                    onApply(selectedLanguage, selectedReminder, mins, autoRefreshChecked)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.btn_apply))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.disclaimer_text),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            //     .
            val context = LocalContext.current
            Button(
                onClick = {
                    val uri = "https://ko-fi.com/queukat".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(R.string.label_support_dev_on_ko_fi))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
@Preview(name = "Settings Light Theme", showBackground = true)
fun PreviewSettingsScreenLight() {
    TrainAppTheme(darkTheme = false) {
        SettingsScreen(
            languages = listOf("me", "en", "meCyr"),
            reminderOptions = listOf("Push", "Calendar", "Both", "None"),
            initialLanguage = "English",
            initialReminder = "Push",
            initialMinutes = 15,
            initialAutoRefresh = true,
            onApply = { _, _, _, _ -> },
            onTestPushNow = {},
            onTestPushLater = {},
            onBackClick = {}
        )
    }
}

@Composable
@Preview(name = "Settings Dark Theme", showBackground = true)
fun PreviewSettingsScreenDark() {
    TrainAppTheme(darkTheme = true) {
        SettingsScreen(
            languages = listOf("me", "en", "meCyr"),
            reminderOptions = listOf("Push", "Calendar", "Both", "None"),
            initialLanguage = "English",
            initialReminder = "Push",
            initialMinutes = 15,
            initialAutoRefresh = true,
            onApply = { _, _, _, _ -> },
            onTestPushNow = {},
            onTestPushLater = {},
            onBackClick = {}
        )
    }
}

