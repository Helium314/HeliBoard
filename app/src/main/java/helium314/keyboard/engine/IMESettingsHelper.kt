package helium314.keyboard.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import helium314.keyboard.engine.general.JapaneseIMESettings
import helium314.keyboard.latin.R
import helium314.keyboard.latin.Subtypes
import helium314.keyboard.latin.SubtypesSetting
import helium314.keyboard.latin.uix.settings.NavigationItemStyle
import helium314.keyboard.latin.uix.settings.UserSettingsMenu
import helium314.keyboard.latin.uix.settings.useDataStoreValue
import helium314.keyboard.latin.uix.settings.userSettingNavigationItem

@Composable
private fun isVisible(language: String): Boolean {
    val subtypeSet = useDataStoreValue(SubtypesSetting)
    return remember(subtypeSet) {
        subtypeSet.any {
            Subtypes.getLocale(Subtypes.convertToSubtype(it).locale).language == language
        }
    }
}

val SettingsByLanguage = mapOf(
    "ja" to JapaneseIMESettings.menu.copy(visibilityCheck = { isVisible("ja") })
)

@Composable
private fun anyVisible(): Boolean {
    val subtypeSet = useDataStoreValue(SubtypesSetting)
    return remember(subtypeSet) {
        subtypeSet.any {
            SettingsByLanguage.containsKey(Subtypes.getLocale(Subtypes.convertToSubtype(it).locale).language)
        }
    }
}

private val IMESettings = buildList {
    SettingsByLanguage.forEach {
        add(
            userSettingNavigationItem(
                title = it.value.title,
                style = NavigationItemStyle.HomePrimary,
                icon = R.drawable.globe,
                navigateTo = it.value.navPath,
            ).copy(
                visibilityCheck = it.value.visibilityCheck,
                appearInSearchIfVisibilityCheckFailed = false
            )
        )
    }
}

val IMESettingsMenu = UserSettingsMenu(
    title = R.string.language_specific_settings_title,
    navPath = "ime", registerNavPath = true,
    settings = IMESettings, visibilityCheck = { anyVisible() }
)