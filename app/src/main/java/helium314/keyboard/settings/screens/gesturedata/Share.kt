package helium314.keyboard.settings.screens.gesturedata

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun ShareGestureData() {
    val ctx = LocalContext.current
    val dao = GestureDataDao.getInstance(ctx)!!
    val hasData = !dao.isEmpty() // todo: should be properly updated, currently it's necessary to exit the screen
    val getDataPicker = getData()

    // get file
    TextButton({ getDataPicker.launch(getDataIntent) }, enabled = hasData) {
        Text(stringResource(R.string.gesture_data_get_data))
    }

    // share file, but only to mail apps
    TextButton(
        onClick = {
            createZipFile(ctx)
            if (zippedDataPath.isNotEmpty())
                ctx.startActivity(createSendIntentChooser(ctx))
        },
        enabled = hasData && Intent(Intent.ACTION_SENDTO)
            .apply { data = "mailto:".toUri() }.resolveActivity(ctx.packageManager) != null
    ) {
        Text("share file to mail")
    }

    // copy mail address to clipboard, in case user doesn't use the mail intent
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { clip.setClipEntry(ClipEntry(ClipData.newPlainText("mail address", MAIL_ADDRESS))) } }) {
        Text("copy mail address")
    }

    // delete data (not sharing, but afterwards)
    TextButton({ dao.deleteAll() }, enabled = hasData) { Text(stringResource(R.string.gesture_data_delete_data)) }
}

private val getDataIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .putExtra(Intent.EXTRA_TITLE, "gesture_data_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)}.zip")
    .setType("application/zip")

// the straightforward way would be to just put the extras in the sendMailIntent and return that,
// but this doesn't attach the file in K9 and Proton

private fun createSendIntentChooser(context: Context): Intent {
    // targets all apps that accept this kind of intent
    val shareFileIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, MAIL_TEXT)
        putExtra(Intent.EXTRA_STREAM, getZipFileUri(context))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareFileIntent, "share it").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // if you are on an android older than N... you will have to just find your email in the list of apps!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            filterIntentToOnlyIncludeEmailApps(context, shareFileIntent)
        }
    }
    return chooser
}

fun Intent.filterIntentToOnlyIncludeEmailApps(context: Context, intentToFilter: Intent) {
    val sendMailIntent = Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri() }
    val mailTargets = context.packageManager.queryIntentActivities(sendMailIntent, 0)
    val shareTargets = context.packageManager.queryIntentActivities(intentToFilter, 0)

    // so now we get the shareTargets that are not mailTargets and create ComponentNames...
    val mailTargetPackages = mailTargets.map { it.activityInfo.packageName }
    val nonMailShareTargets = shareTargets.filterNot { it.activityInfo.packageName in mailTargetPackages }
    val nonMailShareTargetComponentNames = nonMailShareTargets.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    // ... because the chooser only allows to exclude components, but has no multi-component include functionality
    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, nonMailShareTargetComponentNames.toTypedArray())
}

private fun getZipFileUri(context: Context) : Uri =
    FileProvider.getUriForFile(
        context,
        context.getString(R.string.gesture_data_provider_authority),
        getGestureZipFile(context))

@Composable
private fun getData(): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    // check if file exists and not size 0, otherwise don't even offer the button
    return filePicker { uri ->
        val file = getGestureDataFile(ctx)
        val dao = GestureDataDao.getInstance(ctx) ?: return@filePicker
        file.writeText("[${dao.getAllJsonData().joinToString(",")}]")
        ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
            val zipStream = ZipOutputStream(os)
            zipStream.setLevel(9)
            val fileStream = FileInputStream(file).buffered()
            zipStream.putNextEntry(ZipEntry(file.name))
            fileStream.copyTo(zipStream, 1024)
            fileStream.close()
            zipStream.closeEntry()
            zipStream.close()
        }
    }
}

private fun createZipFile(context: Context) : File {
    zippedDataPath = ""
    val jsonFile = getGestureDataFile(context)
    val dao = GestureDataDao.getInstance(context)!!
    jsonFile.writeText("[${dao.getAllJsonData().joinToString(",")}]")
    val zipFile = getGestureZipFile(context)
    zipFile.delete()
    zipFile.outputStream().use { os ->
        val zipStream = ZipOutputStream(os)
        zipStream.setLevel(9)
        val fileStream = FileInputStream(jsonFile).buffered()
        zipStream.putNextEntry(ZipEntry(jsonFile.name))
        fileStream.copyTo(zipStream, 1024)
        fileStream.close()
        zipStream.closeEntry()
        zipStream.close()
    }
    zippedDataPath = zipFile.absolutePath
    return zipFile
}

fun getGestureZipFile(context: Context): File = fileGetDelegate(context, context.getString(R.string.gesture_data_zip))

// necessary for giving the mail app access to an internal file
class GestureFileProvider : FileProvider()

private var zippedDataPath = "" // set after writing the file

private const val MAIL_ADDRESS = "insert mail here"
private const val MAIL_SUBJECT = "Heliboard ${BuildConfig.VERSION_NAME} gesture data"
private const val MAIL_TEXT = "here is gesture data"
