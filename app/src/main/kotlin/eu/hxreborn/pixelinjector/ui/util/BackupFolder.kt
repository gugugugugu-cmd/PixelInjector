package eu.hxreborn.pixelinjector.ui.util

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import eu.hxreborn.pixelinjector.betterfox.BACKUP_MAX_BYTES
import eu.hxreborn.pixelinjector.betterfox.BACKUP_PREFIX
import java.io.IOException

private const val INTERNAL_STORAGE = "Internal storage"
private const val PRIMARY_VOLUME = "primary"
private val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("js") ?: "text/javascript"

class BackupDocument(
    val uri: Uri,
    val name: String,
)

class BackupFolder(
    private val resolver: ContentResolver,
    val tree: Uri?,
) {
    val label: String
        get() {
            val tree = tree ?: return "$INTERNAL_STORAGE/${Environment.DIRECTORY_DOWNLOADS}"
            val id = DocumentsContract.getTreeDocumentId(tree)
            val volume = id.substringBefore(':')
            val path = id.substringAfter(':', "")
            val root = if (volume == PRIMARY_VOLUME) INTERNAL_STORAGE else volume
            return if (path.isEmpty()) root else "$root/$path"
        }

    val folderName: String get() = label.substringAfterLast('/')

    fun list(): List<BackupDocument> = (if (tree == null) listDownloads() else listTree(tree)).filter { isBackupName(it.name) }

    fun write(
        name: String,
        text: String,
    ): BackupDocument {
        val uri = if (tree == null) createDownload(name) else createInTree(tree, name)
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("open $uri")
        return BackupDocument(uri, documentName(resolver, uri, name))
    }

    fun read(uri: Uri): String = readDocument(resolver, uri)

    private fun listDownloads(): List<BackupDocument> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        return resolver
            .query(collection, projection, selection, arrayOf("$BACKUP_PREFIX%"), null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(BackupDocument(Uri.withAppendedPath(collection, cursor.getLong(0).toString()), cursor.getString(1)))
                    }
                }
            }.orEmpty()
    }

    private fun listTree(tree: Uri): List<BackupDocument> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver
            .query(children, projection, null, null, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(BackupDocument(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), cursor.getString(1)))
                    }
                }
            }.orEmpty()
    }

    private fun createDownload(name: String): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert $name")
    }

    private fun createInTree(
        tree: Uri,
        name: String,
    ): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver, parent, mimeType, name) ?: throw IOException("create $name")
    }
}

fun documentName(
    resolver: ContentResolver,
    uri: Uri,
    fallback: String = uri.lastPathSegment.orEmpty(),
): String =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: fallback

fun isBackupName(name: String): Boolean = name.startsWith(BACKUP_PREFIX) && name.endsWith(".js")

fun readDocument(
    resolver: ContentResolver,
    uri: Uri,
): String {
    val bytes =
        resolver.openInputStream(uri)?.use { it.readNBytes(BACKUP_MAX_BYTES + 1) } ?: throw IOException("open $uri")
    if (bytes.size > BACKUP_MAX_BYTES) throw IOException("backup too large")
    return bytes.decodeToString()
}
