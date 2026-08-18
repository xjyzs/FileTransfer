package com.xjyzs.filetransfer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

data class DocumentInfo(
    val name: String,
    val mimeType: String?,
    val size: Long?
)

/** 目录树子项 */
data class ChildInfo(
    val docId: String,
    val name: String,
    val mimeType: String?,
    val size: Long?,
    val isDirectory: Boolean
)

object SafUtils {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    )

    fun queryInfo(context: Context, uri: Uri): DocumentInfo {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    size = if (cursor.isNull(1)) null else cursor.getLong(1)
                }
            }
        }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return DocumentInfo(name ?: displayNameFallback(uri), mime, size)
    }

    fun queryTreeRootInfo(context: Context, treeUri: Uri): DocumentInfo? = try {
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        queryInfo(context, rootDocUri)
    } catch (_: Exception) {
        null
    }

    /** 某节点下的全部子项(目录在前，名称排序) */
    fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<ChildInfo> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val result = mutableListOf<ChildInfo>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                result.add(
                    ChildInfo(
                        docId = docId,
                        name = name,
                        mimeType = mime,
                        size = if (cursor.isNull(3)) null else cursor.getLong(3),
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    )
                )
            }
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun findChild(context: Context, treeUri: Uri, parentDocId: String, name: String): ChildInfo? =
        listChildren(context, treeUri, parentDocId).firstOrNull { it.name == name }

    fun takePersistableReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** 取最后一段路径 */
    private fun displayNameFallback(uri: Uri): String =
        uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: "未命名"
}
