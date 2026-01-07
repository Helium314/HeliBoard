package helium314.keyboard.latin.utils

import android.content.Context
import android.view.ContextThemeWrapper
import helium314.keyboard.latin.R

// todo: ideally the custom InputMethodPicker would be removed / replaced with compose dialog, then this can be removed
fun getPlatformDialogThemeContext(context: Context): Context {
    // Because {@link AlertDialog.Builder.create()} doesn't honor the specified theme with
    // createThemeContextWrapper=false, the result dialog box has unneeded paddings around it.
    return ContextThemeWrapper(context, R.style.platformActivityTheme)
}
