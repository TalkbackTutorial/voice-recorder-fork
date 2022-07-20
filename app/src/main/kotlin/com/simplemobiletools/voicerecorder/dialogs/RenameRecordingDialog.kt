package com.simplemobiletools.voicerecorder.dialogs

import android.content.ContentValues
import android.provider.MediaStore.Audio.Media
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.dialog_rename_recording.view.*
import org.greenrobot.eventbus.EventBus
import java.io.File

class RenameRecordingDialog(val activity: BaseSimpleActivity, val recording: Recording, val callback: () -> Unit) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_rename_recording, null).apply {
            rename_recording_title.setText(recording.title.substringBeforeLast('.'))
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.rename) {
                    showKeyboard(view.rename_recording_title)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = view.rename_recording_title.value
                        if (newTitle.isEmpty()) {
                            activity.toast(R.string.empty_name)
                            return@setOnClickListener
                        }

                        if (!newTitle.isAValidFilename()) {
                            activity.toast(R.string.invalid_name)
                            return@setOnClickListener
                        }

                        ensureBackgroundThread {
                            if (isRPlus()) {
                                updateMediaStoreTitle(recording, newTitle)
                            } else {
                                updateLegacyFilename(recording, newTitle)
                            }

                            activity.runOnUiThread {
                                callback()
                                dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun updateMediaStoreTitle(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val newDisplayName = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"

        val values = ContentValues().apply {
            put(Media.TITLE, newTitle.substringAfterLast('.'))
            put(Media.DISPLAY_NAME, newDisplayName)
        }

        // if the old way of renaming fails, try the new SDK 30 one on Android 11+
        try {
            activity.contentResolver.update(getAudioFileContentUri(recording.id.toLong()), values, null, null)
        } catch (e: Exception) {
            try {
                val path = "${activity.config.saveRecordingsFolder}/${recording.title}"
                val newPath = "${path.getParentPath()}/$newDisplayName"
                activity.handleSAFDialogSdk30(path) {
                    val success = activity.renameDocumentSdk30(path, newPath)
                    if (success) {
                        EventBus.getDefault().post(Events.RecordingCompleted())
                    }
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun updateLegacyFilename(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val oldPath = recording.path
        val newFilename = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"
        val newPath = File(oldPath.getParentPath(), newFilename).absolutePath
        activity.renameFile(oldPath, newPath, false)
    }
}
