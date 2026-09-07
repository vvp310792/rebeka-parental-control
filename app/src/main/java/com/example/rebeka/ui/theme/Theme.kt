package com.example.rebeka.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Фирменный стиль MyGenetics поверх Material 3 — единственное место, где
 * приложение отходит от синих/фиолетовых цветов Material по умолчанию. Все
 * экраны (Home/Onboarding/ParentSettings/PinSetup) уже читают цвета только
 * через MaterialTheme.colorScheme.* / MaterialTheme.typography.* — нигде нет
 * захардкоженных Color(...), поэтому смена темы здесь одна перекрашивает
 * приложение целиком.
 *
 * onPrimary/onSecondary/onTertiary — тёмно-зелёный (MgText), а не белый:
 * у чистого #32CC00 контраст с белым текстом ниже WCAG AA (≈2.1:1), а с
 * тёмным текстом брендбука — около 7:1. Сам брендбук никогда не кладёт текст
 * поверх сплошной заливки акцентом (акцент — только цвет текста/линий на
 * светлом фоне, заливка — у мягкого --mg-accent-soft) — здесь тот же принцип
 * для залитых Material-кнопок.
 *
 * Тёмной темы брендбук не определяет (документ печатный, светлый фон) — как
 * и нативный android:Theme.Rebeka (res/values/themes.xml), здесь тоже одна
 * схема без ветки на ночной режим.
 */
private val MyGeneticsColorScheme = lightColorScheme(
    primary = MgAccent,
    onPrimary = MgText,
    primaryContainer = MgAccentSoft,
    onPrimaryContainer = MgText,

    secondary = MgAccentHover,
    onSecondary = MgText,
    secondaryContainer = MgAccentSoft,
    onSecondaryContainer = MgText,

    tertiary = MgAccentHover,
    onTertiary = MgText,
    tertiaryContainer = MgAccentSoft,
    onTertiaryContainer = MgText,

    background = MgBg,
    onBackground = MgText,

    // Surface — фон "страницы" (см. Surface(Modifier.fillMaxSize()) в MainActivity).
    surface = MgBg,
    onSurface = MgText,
    surfaceVariant = MgBgSoft,
    onSurfaceVariant = MgTextDim,

    // surfaceContainer* — то, что реально красит фон Card по умолчанию в Material 3.
    // Слегка отличается от MgBg, иначе карточки сливаются со страницей без бордера.
    surfaceContainerLowest = MgBg,
    surfaceContainerLow = MgBgSoft,
    surfaceContainer = MgBgSoft,
    surfaceContainerHigh = MgAccentSoft,
    surfaceContainerHighest = MgAccentSoft,

    // Тонкие рамки/разделители (HorizontalDivider, OutlinedButton, OutlinedTextField).
    outline = MgBorder,
    outlineVariant = MgBorder
)

/**
 * Радиус карточек — по токену брендбука (8px). Кнопки у Material 3 уже
 * полностью скруглены (pill) по умолчанию — совпадает с токеном "100px"
 * из брендбука без дополнительных правок.
 */
private val MyGeneticsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun RebekaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MyGeneticsColorScheme,
        shapes = MyGeneticsShapes,
        content = content
    )
}
