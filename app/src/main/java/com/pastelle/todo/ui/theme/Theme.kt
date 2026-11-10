package com.pastelle.todo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 全套色彩令牌组装（本期只做浅色，不跟随系统深色模式） */
private val PastelleColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = CardWhite,
    secondary = Purple,
    onSecondary = CardWhite,
    tertiary = Peach,
    onTertiary = CardWhite,
    background = ScreenBase,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Track,
    onSurfaceVariant = InkSoft,
    outline = Track,
    error = Coral,
    onError = CardWhite,
)

private val PastelleMaterialShapes = Shapes(
    small = PastelleShapes.Face,
    medium = PastelleShapes.Card,
    large = PastelleShapes.Card,
)

@Composable
fun PastelleTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PastelleColorScheme,
        typography = PastelleTypography,
        shapes = PastelleMaterialShapes,
        content = content,
    )
}
