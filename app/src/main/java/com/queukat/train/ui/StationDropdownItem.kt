package com.queukat.train.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text

@Composable
fun StationDropdownItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            // :  marginLeft="48dp" marginRight="48dp" ( )
            .padding(horizontal = 48.dp)
            //  layout_marginVertical="4dp"
            .padding(vertical = 4.dp)
            //   (  MaterialTheme.colorScheme.surface  CustomBackground)
            .background(MaterialTheme.colorScheme.surface)
            //  
            .border(width = 1.dp, color = Color(0xFFD0D0D0))
            // paddingHorizontal="12dp" paddingVertical="8dp"
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            // android:textColor="#000" → Color.Black
            color = Color.Black,
            // android:textSize="14sp"
            fontSize = 14.sp,
            // android:gravity="center_vertical"
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
