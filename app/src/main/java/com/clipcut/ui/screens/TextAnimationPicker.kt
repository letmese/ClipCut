package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.TextAnimation
import com.clipcut.ui.theme.*

@Composable
fun TextAnimationPicker(
    selected: TextAnimation,
    onSelect: (TextAnimation) -> Unit
) {
    Column {
        Text("Animation", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimation.values().toList()) { anim ->
                val isSelected = anim == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentPrimary else DarkSurface)
                        .clickable { onSelect(anim) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        anim.displayName,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
