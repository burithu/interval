package io.github.burithu.interval.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Auto Backup still does the backing up (fullBackupOnly). This agent only notices that a
 * restore happened, so the app can treat the restored data as coming from another phone:
 * fresh ring times, setup run again (D37). It runs before the app's own code, so it only
 * leaves a marker in no-backup storage for [io.github.burithu.interval.IntervalApp] to act on.
 */
class RestoreAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        File(noBackupFilesDir, MARKER).createNewFile()
    }

    companion object {
        const val MARKER = "restored"
    }
}
