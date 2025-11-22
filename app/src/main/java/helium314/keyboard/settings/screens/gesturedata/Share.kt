// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens.gesturedata

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
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
import helium314.keyboard.latin.utils.GestureDataDao
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

// todo: nicer looking buttons
@Composable
fun ShareGestureData(ids: List<Long>) { // should we really use null here? from where this is called we have all ids anyway
    val ctx = LocalContext.current
    val dao = GestureDataDao.getInstance(ctx)!!
    val hasData = !dao.isEmpty() // no need to update if we have it in a dialog
    val getDataPicker = getData(ids)
    gestureIdsBeingExported = null

    // share file, but only to mail apps
    TextButton(
        onClick = {
            createZipFile(ctx, ids)
            if (zippedDataPath.isNotEmpty()) {
                gestureIdsBeingExported = ids
                ctx.startActivity(createSendIntentChooser(ctx))
            }
        },
        enabled = hasData && Intent(Intent.ACTION_SENDTO)
            .apply { data = "mailto:".toUri() }.resolveActivity(ctx.packageManager) != null
    ) {
        Text("share via mail")
    }

    // get file
    TextButton({ getDataPicker.launch(getDataIntent) }, enabled = hasData) {
        Text(stringResource(R.string.gesture_data_get_data))
    }

    // copy mail address to clipboard, in case user doesn't use the mail intent
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { clip.setClipEntry(ClipEntry(ClipData.newPlainText("mail address", ctx.getString(R.string.gesture_data_mail)))) } }) {
        Text("copy mail address")
    }

    // this deletes the data in the dialog, but we should also have a way of deleting previously exported data
    // todo: does the dialog stay / list stay after exporting? -> yes
    TextButton({ if (ids == null) dao.deleteAll() else dao.delete(ids) }, enabled = hasData) {
        Text("delete exported data (only do this after mail has been sent)") // todo: consistent & clear naming (export / share)
    }
}

private val getDataIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .putExtra(Intent.EXTRA_TITLE, "gesture_data_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)}.zip")
    .setType("application/zip")

// the straightforward way would be to just put the extras in the sendMailIntent and return that,
// but this doesn't attach the file in K9 and Proton

private fun createSendIntentChooser(context: Context): Intent {
    // targets all apps that accept this kind of intent
    val shareFileIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.gesture_data_mail)))
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

private fun Intent.filterIntentToOnlyIncludeEmailApps(context: Context, intentToFilter: Intent) {
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
private fun getData(ids: List<Long>): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    // check if file exists and not size 0, otherwise don't even offer the button
    return filePicker { uri ->
        val file = getGestureDataFile(ctx)
        val dao = GestureDataDao.getInstance(ctx) ?: return@filePicker
        val data = dao.getJsonData(ids)
        file.writeText("[${data.joinToString(",\n")}]")
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
        dao.markAsExported(ids)
    }
}

private fun createZipFile(context: Context, ids: List<Long>) : File {
    zippedDataPath = ""
    val jsonFile = getGestureDataFile(context)
    val dao = GestureDataDao.getInstance(context)!!
    val data = dao.getJsonData(ids)
    jsonFile.writeText("[${data.joinToString(",\n")}]")
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

private fun getGestureZipFile(context: Context): File = fileGetDelegate(context, context.getString(R.string.gesture_data_zip))

private fun getGestureDataFile(context: Context): File = fileGetDelegate(context, context.getString(R.string.gesture_data_json))

private fun fileGetDelegate(context: Context, filename: String): File {
    // ensure folder exists
    val dir = File(context.filesDir, context.getString(R.string.gesture_data_directory))
    if (!dir.exists()) {
        dir.mkdirs()
    }

    return File(dir, filename)
}

// necessary for giving the mail app access to an internal file
// overrides to check when data is actually read (i.e. chooser not cancelled)
class GestureFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        try {
            return super.openFile(uri, mode, signal)
        } finally {
            val ctx = context
            val ids = gestureIdsBeingExported
            if (ctx != null && ids != null) {
                GestureDataDao.getInstance(ctx)?.markAsExported(ids)
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        try {
            return super.openFile(uri, mode)
        } finally {
            val ctx = context
            val ids = gestureIdsBeingExported
            if (ctx != null && ids != null) {
                GestureDataDao.getInstance(ctx)?.markAsExported(ids)
            }
        }
    }
}

private var gestureIdsBeingExported: List<Long>? = null

private var zippedDataPath = "" // set after writing the file

private const val MAIL_SUBJECT = "Heliboard ${BuildConfig.VERSION_NAME} gesture data"
private const val MAIL_TEXT = "here is gesture data"
