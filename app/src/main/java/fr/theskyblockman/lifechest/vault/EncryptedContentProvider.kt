package fr.theskyblockman.lifechest.vault

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Log
import fr.theskyblockman.lifechest.BuildConfig
import fr.theskyblockman.lifechest.vault.Crypto.DEFAULT_CIPHER_NAME
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider.Companion.vault
import java.io.RandomAccessFile
import javax.crypto.Cipher

/**
 * A content provider enabling reading the content of encrypted files using URIs.
 *
 * Here's the scheme:
 * ```plain
 * content://fr.theskyblockman.lifechest.encryptedfiles/<vault-id>/<file-id>
 * ```
 *
 * In no event shall one of those URIs be shared outside of the app or to the user.
 *
 * Those URIs only work when the app is running in the foreground and the vault is unlocked.
 * (when [vault] is set)
 */
class EncryptedContentProvider : ContentProvider() {
    companion object {
        lateinit var vault: Vault

        private val COLUMNS: Array<out String> = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaColumns.DATE_ADDED
        )

        // Could be
        // - fr.theskyblockman.lifechest.encryptedfiles
        // - fr.theskyblockman.lifechest.debug.encryptedfiles
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.encryptedfiles"


        fun getUriFromFile(file: FileNode): Uri {
            return Uri.parse("content://$AUTHORITY/${file.vaultID}/${file.id}")
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    private fun getFile(uri: Uri): FileNode? {
        if (uri.authority != AUTHORITY) {
            return null
        }

        if (uri.pathSegments.size != 2) {
            return null
        }

        val vaultID = uri.pathSegments[0]
        val fileID = uri.pathSegments[1]

        if (vault.id != vaultID) {
            return null
        }

        val file = vault.fileTree!!.goTo(fileID) ?: return null

        if (file !is FileNode) {
            return null
        }

        return file
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = getFile(uri) ?: return null

        val newProjection = projection ?: COLUMNS

        var cols = arrayOfNulls<String>(newProjection.size)
        var values = arrayOfNulls<Any>(newProjection.size)

        var i = 0

        for (col in newProjection) {
            if (OpenableColumns.DISPLAY_NAME == col) {
                cols[i] = OpenableColumns.DISPLAY_NAME
                values[i++] = file.name
            } else if (OpenableColumns.SIZE == col) {
                cols[i] = OpenableColumns.SIZE
                values[i++] = file.size
            } else if (MediaColumns.DATE_ADDED == col) {
                cols[i] = MediaColumns.DATE_ADDED
                values[i++] = file.importDate
            }
        }

        cols = cols.copyOf(i)
        values = values.copyOf(i)

        val cursor = MatrixCursor(cols, 1)
        cursor.addRow(values)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return getFile(uri)?.type
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Only the app code should be able to edit the files in any way
        throw UnsupportedOperationException("No external deletions")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw java.lang.UnsupportedOperationException("No external updates")
    }

    private val handlerThread = HandlerThread("BackgroundFileReader")

    init {
        handlerThread.start()
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw UnsupportedOperationException("Only reading is supported")
        }

        val file = getFile(uri) ?: return null

        val storageManager = context?.getSystemService(Context.STORAGE_SERVICE) as StorageManager?
            ?: throw SecurityException("No storage manager")

        if (vault.id != file.vaultID) {
            throw SecurityException("Vault mismatch")
        }

        val randomAccessFile =
            Crypto.Decrypt.FileProgressively.getInputStreamForFile(vault, file.attachedFile)
        return storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY, EncryptedProxyFileDescriptorCallback(
                file,
                randomAccessFile
            ), Handler(handlerThread.looper)
        )
    }

    private inner class EncryptedProxyFileDescriptorCallback(
        private val file: FileNode,
        private val randomAccessFile: RandomAccessFile
    ) : ProxyFileDescriptorCallback() {
        private var currentCipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)
        private var lastOffset: Long = 0

        override fun onGetSize(): Long {
            return file.size
        }

        override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
            if (data == null) return if (offset + size > file.size) (file.size - offset).toInt() else size

            val result = Crypto.Decrypt.FileProgressively.readWith(
                currentCipher!!, vault.key!!, file.attachedFile.iv, offset, size.toLong(),
                data, randomAccessFile
            )

            lastOffset += result

            return result.toInt()
        }

        init {
            Log.d("EncryptedContentProvider", "Initializing file")
        }

        override fun onRelease() {
            Log.d("EncryptedContentProvider", "Releasing file")
            randomAccessFile.close()
        }
    }
}