package com.vkmusic.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import com.vkmusic.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Куда пишутся сохранённые треки. Папка из настроек — дерево SAF (ACTION_OPEN_DOCUMENT_TREE);
 * без неё — «Музыка/MuVK» через MediaStore (Android 10+, разрешения не нужны), на Android 8–9 —
 * папка приложения Android/data/…/Music/MuVK.
 */
@Singleton
class ExportStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    /** @return человекочитаемое место сохранения. */
    suspend fun save(subfolder: String?, fileName: String, mimeType: String, data: ByteArray): String = withContext(Dispatchers.IO) {
        val tree = settingsRepository.settings.first().exportTreeUri
        val folder = subfolder?.let(::sanitize)?.takeIf { it.isNotBlank() }
        when {
            tree != null -> saveToTree(tree.toUri(), folder, fileName, mimeType, data)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(folder, fileName, mimeType, data)
            else -> saveToAppFolder(folder, fileName, data)
        }
    }

    fun displayName(treeUri: String?): String {
        if (treeUri == null) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Музыка/$DEFAULT_FOLDER" else "Android/data/…/Music/$DEFAULT_FOLDER"
        }
        val id = runCatching { DocumentsContract.getTreeDocumentId(treeUri.toUri()) }.getOrNull() ?: return treeUri
        return id.substringAfter(':').ifEmpty { id }
    }

    private fun saveToTree(tree: Uri, folder: String?, fileName: String, mimeType: String, data: ByteArray): String {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        if (folder != null) {
            parent = findChildDirectory(tree, DocumentsContract.getDocumentId(parent), folder)
                ?: DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, folder)
                ?: throw IOException("Не удалось создать папку «$folder»")
        }
        // Провайдер сам добавит « (1)», если файл с таким именем уже есть.
        val file = try {
            DocumentsContract.createDocument(resolver, parent, mimeType, fileName)
        } catch (e: SecurityException) {
            throw IOException("Нет доступа к папке сохранения — выберите её заново в настройках", e)
        } ?: throw IOException("Не удалось создать файл «$fileName»")
        resolver.openOutputStream(file)?.use { it.write(data) } ?: throw IOException("Не удалось записать «$fileName»")
        return listOfNotNull(displayName(tree.toString()), folder, fileName).joinToString("/")
    }

    private fun findChildDirectory(tree: Uri, parentId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(folder: String?, fileName: String, mimeType: String, data: ByteArray): String {
        val resolver = context.contentResolver
        val relativePath = listOfNotNull(Environment.DIRECTORY_MUSIC, DEFAULT_FOLDER, folder).joinToString("/") + "/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = resolver.insert(collection, values) ?: throw IOException("Не удалось создать файл «$fileName»")
        try {
            resolver.openOutputStream(item)?.use { it.write(data) } ?: throw IOException("Не удалось записать «$fileName»")
            resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
        return relativePath + fileName
    }

    private fun saveToAppFolder(folder: String?, fileName: String, data: ByteArray): String {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: throw IOException("Хранилище недоступно")
        val dir = File(root, listOfNotNull(DEFAULT_FOLDER, folder).joinToString("/")).apply { mkdirs() }
        var target = File(dir, fileName)
        var index = 1
        while (target.exists()) target = File(dir, "${fileName.substringBeforeLast('.')} (${index++}).${fileName.substringAfterLast('.')}")
        target.writeBytes(data)
        return target.absolutePath
    }

    companion object {
        const val DEFAULT_FOLDER = "MuVK"

        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trim('.').take(MAX_NAME_LENGTH)

        private const val MAX_NAME_LENGTH = 120
    }
}
