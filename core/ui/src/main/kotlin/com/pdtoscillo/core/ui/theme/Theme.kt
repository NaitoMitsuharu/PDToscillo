package com.pdtoscillo.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 波形の描画色。
 *
 * Tektronix のフロントパネル表示に合わせる。CH1 黄、CH2 シアン、CH3 マゼンタ、CH4 緑。
 * 実機の色と揃えることで、画面と本体を見比べたときに対応が分かる。
 */
object TraceColors {
    val ch1 = Color(0xFFFFEB3B)
    val ch2 = Color(0xFF00E5FF)
    val ch3 = Color(0xFFFF4FD8)
    val ch4 = Color(0xFF69F0AE)
    val math = Color(0xFFFF8A65)
    val reference = Color(0xFFB0BEC5)
    val rf = Color(0xFFFFAB40)
    val digital = Color(0xFF80D8FF)

    /** グリッドと目盛。波形より控えめにする。 */
    val graticule = Color(0xFF37474F)
    val graticuleCenter = Color(0xFF546E7A)
    val screenBackground = Color(0xFF0B0F0D)
    val triggerMarker = Color(0xFFFF7043)
    val cursor = Color(0xFFFFFFFF)

    fun forAnalogChannel(channel: Int): Color = when (channel) {
        1 -> ch1
        2 -> ch2
        3 -> ch3
        4 -> ch4
        else -> reference
    }

    fun forDigitalBit(bit: Int): Color = digital.copy(alpha = 0.6f + (bit % 4) * 0.1f)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CFFB2),
    onPrimary = Color(0xFF00341C),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFF9BFFC8),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF00344A),
    tertiary = Color(0xFFFFD180),
    onTertiary = Color(0xFF3E2600),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C3),
    outline = Color(0xFF89938E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9BFFC8),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF00658C),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
)

/**
 * 数値表示用の等幅書体。
 *
 * 測定値が更新されるたびに桁幅が変わって行が揺れるのを防ぐ。
 */
val MonospaceNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
)

private val PdtTypography = Typography()

/** タッチターゲットの最小寸法。計測器操作では取り違えを避けるため十分に大きく取る。 */
val MinTouchTarget = 48.dp

@Composable
fun PdtTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PdtTypography,
        content = content,
    )
}
