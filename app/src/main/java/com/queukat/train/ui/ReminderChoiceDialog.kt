package com.queukat.train.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.train.R
import com.queukat.train.data.model.DirectRoute
import androidx.core.content.edit

/**
 *     (Push / Calendar / Both / None)  - .
 */
@Composable
fun ReminderChoiceDialog(
    route: DirectRoute,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onActionChosen: (String, Int) -> Unit
) {
    val trainNumber = route.TrainNumber ?: "Unknown"
    var selectedAction by remember { mutableStateOf("push") }
    var minutesInput by remember { mutableStateOf("15") }
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_reminder_title)) },
        text = {
            Column {
                Text("Train: $trainNumber")
                Spacer(modifier = Modifier.height(8.dp))

                // 
                RadioItem(
                    label = stringResource(R.string.radio_push),
                    selected = (selectedAction == "push")
                ) {
                    selectedAction = "push"
                }
                RadioItem(
                    label = stringResource(R.string.radio_calendar),
                    selected = (selectedAction == "calendar")
                ) {
                    selectedAction = "calendar"
                }
                RadioItem(
                    label = stringResource(R.string.radio_both),
                    selected = (selectedAction == "both")
                ) {
                    selectedAction = "both"
                }
                RadioItem(
                    label = stringResource(R.string.radio_none),
                    selected = (selectedAction == "none")
                ) {
                    selectedAction = "none"
                }

                Spacer(modifier = Modifier.height(8.dp))
                //  "  "
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { minutesInput = it },
                    label = { Text(stringResource(R.string.hint_reminder_minutes)) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text(text = stringResource(R.string.checkbox_remember_choice))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mins = minutesInput.toIntOrNull() ?: 15
                    //      :
                    if (rememberChoice) {
                        prefs.edit() {
                            putString("defaultReminderAction", selectedAction)
                                .putInt("defaultMinutesBefore", mins)
                        }
                    }
                    onActionChosen(selectedAction, mins)
                }
            ) {
                Text(text = stringResource(R.string.dialog_reminder_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_reminder_cancel))
            }
        }
    )
}

/**  composable      RadioButton  Text. */
@Composable
private fun RadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label)
    }
}
