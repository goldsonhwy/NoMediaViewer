package com.nomedia.viewer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun YellowLoadingOverlay(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val pulse by rememberInfiniteTransition(label = "yellow-loading").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
            label = "pulse"
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yellow", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Surface(color = Color(0xFFFFB000), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(start = 4.dp)) {
                    Text("gallery", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(150.dp).height(4.dp).background(Color(0xFF222222), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pulse).background(Color(0xFFFFB000), RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
