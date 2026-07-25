package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RightScrollProgressBar(progress: Float, visibleFraction: Float = 0.12f, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    val vf = visibleFraction.coerceIn(0.06f, 1f)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        val barHeight = maxHeight * vf
        val y = (maxHeight - barHeight) * p
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x11000000)).align(Alignment.CenterStart))
        Box(
            Modifier
                .offset(y = y)
                .width(3.dp)
                .height(barHeight)
                .background(Color(0xFFFFB000), RoundedCornerShape(3.dp))
                .align(Alignment.TopStart)
        )
    }
}
