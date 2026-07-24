package com.nomedia.viewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GreenCorner(
    modifier: Modifier = Modifier,
    topRight: Boolean = false,
    size: Dp = 14.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path()
        if (topRight) {
            path.moveTo(w, 0f)
            path.lineTo(0f, 0f)
            path.lineTo(w, h)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            path.lineTo(0f, h)
        }
        path.close()
        drawPath(path, Color(0xFFFFB000))
    }
}
