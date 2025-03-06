package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults.default
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.ScriptUtils.script
import java.io.File
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
        val layouts = SubtypeSettings.getResourceSubtypesForLocale(locale).mapNotNullTo(mutableSetOf()) { it.mainLayoutName() }
        if (locale.script() == ScriptUtils.SCRIPT_LATIN)
            layouts.addAll(context.resources.getStringArray(R.array.predefined_layouts))
        return layouts
    }

    fun getLMainLayoutsForLocales(locales: List<Locale>, context: Context): Collection<String> =
        locales.flatMapTo(HashSet()) { getAvailableLayouts(LayoutType.MAIN, context, it) }.sorted()

    fun getContent(layoutType: LayoutType, layoutName: String, context: Context): String {
        val layouts = context.assets.list(layoutType.folder)!!
        layouts.firstOrNull { it.startsWith("$layoutName.") }
            ?.let { return context.assets.open(layoutType.folder + File.separator + it).reader().readText() }
        val fallback = layouts.first { it.startsWith(layoutType.default) } // must exist!
        return context.assets.open(layoutType.folder + File.separator + fallback).reader().readText()
    }
}
