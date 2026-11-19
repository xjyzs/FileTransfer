package com.xjyzs.filetransfer.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    var isInitialized: Boolean = false
        private set

    val _errorMsg = MutableStateFlow("")
    val errorMsg = _errorMsg.asStateFlow()
    val _errorDialogExpanded = MutableStateFlow(false)
    val errorDialogExpanded = _errorDialogExpanded.asStateFlow()
    val _host = MutableStateFlow("::") // IP
    val host = _host.asStateFlow()
    val _ipLst = MutableStateFlow<List<String>>(emptyList())
    val ipLst = _ipLst.asStateFlow()
    val _port = MutableStateFlow("1145")
    val port = _port.asStateFlow()
    val _serverRunning = MutableStateFlow(false)
    val serverRunning = _serverRunning.asStateFlow()
    val _enablePreview = MutableStateFlow(true)
    val enablePreview = _enablePreview.asStateFlow()
    val requestLogs = mutableStateListOf<Request>()
    val selectedFiles = mutableStateListOf<PickedFile>()

    suspend fun ensureInitialized(context: Context): Boolean = initMutex.withLock {
        if (isInitialized) return@withLock false

        withContext(Dispatchers.IO) {
            val sp =
                context.applicationContext.getSharedPreferences("options", Context.MODE_PRIVATE)
            _port.value = sp.getString("port", "1145") ?: "1145"
            _enablePreview.value = sp.getBoolean("enablePreview", true)

            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                _port.drop(1).collect { value ->
                    sp.edit { putString("port", value) }
                }
            }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                _enablePreview.drop(1).collect { value ->
                    sp.edit { putBoolean("enablePreview", value) }
                }
            }

            val ips = fetchNetworkIps()
            val preferredIp = ips.lastOrNull { "192." in it }
            if (preferredIp != null) {
                _host.value = preferredIp
            } else if (_host.value.isEmpty()) {
                _host.value = "127.0.0.1"
            }
            _ipLst.value = ips
            isInitialized = true
        }
        true
    }

    suspend fun refreshIpList() {
        initMutex.withLock {
            if (!isInitialized) return@withLock
            withContext(Dispatchers.IO) {
                val newIps = fetchNetworkIps().toMutableList()
                val currentHost = _host.value
                if (currentHost.isNotEmpty() && currentHost !in newIps) {
                    newIps.add(currentHost)
                }
                _ipLst.value = newIps
            }
        }
    }

    private fun fetchNetworkIps(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (intf in interfaces) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress) {
                            val addrStr = addr.hostAddress ?: "unknown"
                            if (!addrStr.contains("fe80")) {
                                result.add(addrStr)
                            }
                        }
                    }
                }
            }
        }
        result.add("127.0.0.1")
        result.add("::")
        return result
    }
}
