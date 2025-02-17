package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults.default
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.ScriptUtils.script
import java.util.Locale

// for layouts provided by the app
object LayoutUtils {
    fun getAvailableLayouts(layoutType: LayoutType, context: Context, locale: Locale? = null): Collection<String> {
        if (layoutType != LayoutType.MAIN)
            return context.assets.list(layoutType.folder)?.map { it.substringBefore(".") }.orEmpty()
        if (locale == null)
            return SubtypeSettings.getAllAvailableSubtypes()
                .mapTo(HashSet()) { it.mainLayoutName()?.substringBefore("+") ?: "qwerty" }
                .apply { addAll(context.resources.getStringArray(R.array.predefined_layouts)) }
        if (locale.script() == ScriptUtils.SCRIPT_LATIN)
            return context.resources.getStringArray(R.array.predefined_layouts).toList()
        return SubtypeSettings.getResourceSubtypesForLocale(locale).mapNotNullTo(HashSet()) { it.mainLayoutName() }
    }

    fun getLMainLayoutsForLocales(locales: List<Locale>, context: Context): Collection<String> =
        locales.flatMapTo(HashSet()) { getAvailableLayouts(LayoutType.MAIN, context, it) }.sorted()

    fun getContent(layoutType: LayoutType, layoutName: String, context: Context): String {
        val layouts = context.assets.list(layoutType.folder)!!
        layouts.firstOrNull { it.startsWith("$layoutName.") }
            ?.let { return context.assets.open(layoutType.folder + it).reader().readText() }
        val fallback = layouts.first { it.startsWith(layoutType.default) } // must exist!
        return context.assets.open(layoutType.folder + fallback).reader().readText()
    }
}
