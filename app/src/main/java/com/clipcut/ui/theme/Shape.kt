package com.clipcut.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3 Shapes matching the design-system radii: 8/10/14/18/24dp.
 * The API reference declares: extraSmall=4dp, small=8dp, medium=10dp,
 * large=14dp, extraLarge=24dp. We also expose two extra alias shapes
 * (18dp for cards, 24dp for sheets) used by UI components.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val ShapeSmall = RoundedCornerShape(8.dp)
val ShapeMedium = RoundedCornerShape(10.dp)
val ShapeLarge = RoundedCornerShape(14.dp)
val ShapeXLarge = RoundedCornerShape(18.dp)
val ShapeXXLarge = RoundedCornerShape(24.dp)
