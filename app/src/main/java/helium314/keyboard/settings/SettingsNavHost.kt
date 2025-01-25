// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun SettingsNavHost(
    onClickBack: () -> Unit,
    startDestination: String? = null,
) {
    val navController = rememberNavController()
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    fun goBack() {
        if (!navController.popBackStack()) onClickBack()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination ?: SettingsDestination.Settings,
        enterTransition = { slideInHorizontally(initialOffsetX = { +it * dir }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it * dir }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it * dir }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { +it * dir }) }
    ) {
        composable(SettingsDestination.Settings) {
            MainSettingsScreen(
                onClickAbout = { navController.navigate(SettingsDestination.About) },
                onClickTextCorrection = { navController.navigate(SettingsDestination.TextCorrection) },
                onClickBack = ::goBack,
            )
        }
        composable(SettingsDestination.About) {
            AboutScreen(
                onClickBack = ::goBack
            )
        }
        composable(SettingsDestination.TextCorrection) {
            TextCorrectionScreen (
                onClickBack = ::goBack
            )
        }
    }
}

object SettingsDestination {
    const val Settings = "settings"
    const val About = "about"
    const val TextCorrection = "text_correction"
}
