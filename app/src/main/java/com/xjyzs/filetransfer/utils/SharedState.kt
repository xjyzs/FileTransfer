package com.xjyzs.filetransfer.utils

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Request(
    val ip: String, val time: Long, val pth: String, val result: Result
)

enum class Result {
    GET_FILE, LIST_DIR, UPLOAD_FILE
}

data class PickedFile(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String? = null,
    val size: Long? = null
)

object SharedState {
    val _errorMsg = MutableStateFlow("")
    val errorMsg = _errorMsg.asStateFlow()
    val _errorDialogExpanded = MutableStateFlow(false)
    val errorDialogExpanded = _errorDialogExpanded.asStateFlow()
    val _host = MutableStateFlow("::") // IP
    val host = _host.asStateFlow()
    val _port = MutableStateFlow("1145")
    val port = _port.asStateFlow()
    val _serverRunning = MutableStateFlow(false)
    val serverRunning = _serverRunning.asStateFlow()
    val requestLogs = mutableStateListOf<Request>()
    val selectedFiles = mutableStateListOf<PickedFile>()
}
