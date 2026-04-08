package com.queukat.train.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusBanner(
    notice: UiNotice,
    modifier: Modifier = Modifier
) {
    val containerColor = when (notice.tone) {
        UiNoticeTone.Info -> MaterialTheme.colorScheme.surfaceVariant
        UiNoticeTone.Success -> MaterialTheme.colorScheme.primaryContainer
        UiNoticeTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        UiNoticeTone.Error -> MaterialTheme.colorScheme.errorContainer
    }

    val textColor = when (notice.tone) {
        UiNoticeTone.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        UiNoticeTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        UiNoticeTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        UiNoticeTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Text(
        text = notice.message,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(containerColor, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
