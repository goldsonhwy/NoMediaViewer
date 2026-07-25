package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RightScrollProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(Color(0x22000000)))
        Box(Modifier.width(2.dp).fillMaxHeight(p).background(Color(0xFFFFB000)).align(Alignment.TopEnd))
    }
}
