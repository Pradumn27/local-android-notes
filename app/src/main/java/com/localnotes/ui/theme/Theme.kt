package com.localnotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NotesYellow = Color(0xFFF5C518)
val NotesGold = Color(0xFFC4A016)
val FolderYellow = Color(0xFFF2C94C)
val NotesLink = Color(0xFF0A84FF)
val NotesRed = Color(0xFFFF3B30)

@Immutable
data class NotesColors(
    val sidebar: Color,
    val list: Color,
    val editor: Color,
    val toolbar: Color,
    val search: Color,
    val rowSelected: Color,
    val sidebarSelected: Color,
    val separator: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val gold: Color,
    val yellow: Color,
    val folder: Color,
    val destructive: Color,
    val link: Color,
    val checkFill: Color,
    val overlay: Color,
    val isDark: Boolean,
)

val LightNotesColors = NotesColors(
    sidebar = Color(0xFFEFEFF4),
    list = Color(0xFFFFFFFF),
    editor = Color(0xFFFFFFFF),
    toolbar = Color(0xFFF7F7F8),
    search = Color(0xFFE3E3E8),
    rowSelected = Color(0xFFFFF1B8),
    sidebarSelected = Color(0xFFD8D8DE),
    separator = Color(0xFFE5E5EA),
    label = Color(0xFF1C1C1E),
    secondary = Color(0xFF8E8E93),
    tertiary = Color(0xFFAEAEB2),
    gold = NotesGold,
    yellow = NotesYellow,
    folder = FolderYellow,
    destructive = NotesRed,
    link = Color(0xFF007AFF),
    checkFill = NotesYellow,
    overlay = Color(0x99000000),
    isDark = false,
)

val DarkNotesColors = NotesColors(
    sidebar = Color(0xFF1C1C1E),
    list = Color(0xFF1C1C1E),
    editor = Color(0xFF1C1C1E),
    toolbar = Color(0xFF2C2C2E),
    search = Color(0xFF2C2C2E),
    rowSelected = Color(0xFF3D3420),
    sidebarSelected = Color(0xFF3A3A3C),
    separator = Color(0xFF2C2C2E),
    label = Color(0xFFF2F2F7),
    secondary = Color(0xFF8E8E93),
    tertiary = Color(0xFF636366),
    gold = Color(0xFFE0C04A),
    yellow = NotesYellow,
    folder = FolderYellow,
    destructive = NotesRed,
    link = NotesLink,
    checkFill = NotesYellow,
    overlay = Color(0x99000000),
    isDark = true,
)

val LocalNotesColors = staticCompositionLocalOf { LightNotesColors }

val NotesTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    ),
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkNotesColors else LightNotesColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = NotesYellow,
            onPrimary = Color.Black,
            background = colors.editor,
            surface = colors.list,
            onBackground = colors.label,
            onSurface = colors.label,
            error = NotesRed,
        )
    } else {
        lightColorScheme(
            primary = NotesYellow,
            onPrimary = Color.Black,
            background = colors.editor,
            surface = colors.list,
            onBackground = colors.label,
            onSurface = colors.label,
            error = NotesRed,
        )
    }
    CompositionLocalProvider(
        LocalNotesColors provides colors,
        LocalContentColor provides colors.label,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NotesTypography,
            content = content,
        )
    }
}
