package com.queukat.train.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 *    dropdown item,     /.
 */
@Composable
fun DropdownItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = Color(0xFFD0D0D0),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        content()
    }
}
