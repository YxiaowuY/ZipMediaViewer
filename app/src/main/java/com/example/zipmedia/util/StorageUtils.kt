package com.example.zipmedia.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

data class ArchiveFileInfo(
    val name: String,
    val uri: Uri,
    val display: String
)

/**
 * 通过 Storage Access Framework 浏览用户选择的预览文件夹，
 * 递归找出其中所有受支持的压缩包（zip / rar）。
 */
object StorageUtils {

    private val SUPPORTED = setOf("zip", "rar")

    /** 递归收集文件夹内的压缩包，depth 限制层级防止过深 */
    fun collectArchives(context: Context, treeUri: Uri, depth: Int = 4): List<ArchiveFileInfo> {
        val result = mutableListOf<ArchiveFileInfo>()
        walk(context, treeUri, depth, result)
        return result
    }

    private fun walk(context: Context, dirUri: Uri, depth: Int, out: MutableList<ArchiveFileInfo>) {
        if (depth < 0) return
        listChildren(context, dirUri).forEach { (childUri, displayName, isDir) ->
            if (isDir) {
                walk(context, childUri, depth - 1, out)
            } else {
                val ext = displayName.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED) {
                    out.add(
                        ArchiveFileInfo(
                            name = displayName,
                            uri = childUri,
                            display = displayName
                        )
                    )
                }
            }
        }
    }

    private fun listChildren(context: Context, parentUri: Uri): List<Triple<Uri, String, Boolean>> {
        val result = mutableListOf<Triple<Uri, String, Boolean>>()
        val docsUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getTreeDocumentId(parentUri)
        )
        runCatching {
            context.contentResolver.query(
                docsUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, id)
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mime
                    if (isDir || name.isNotBlank()) {
                        result.add(Triple(childUri, name, isDir))
                    }
                }
            }
        }
        return result
    }
}