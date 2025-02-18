// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import helium314.keyboard.latin.R

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val material3 = Typography()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        // todo (later): more colors
        if (dark) darkColorScheme(
            primary = colorResource(R.color.accent),
        )
        else lightColorScheme(
            primary = colorResource(R.color.accent)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineMedium = material3.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = material3.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = material3.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold))
            ),
            titleMedium = material3.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold))
            ),
            titleSmall = material3.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold))
            )
        ),
        //shapes = Shapes(),
        content = content
    )
}
