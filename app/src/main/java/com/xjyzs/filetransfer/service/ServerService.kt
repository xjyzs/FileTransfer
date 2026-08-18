package com.xjyzs.filetransfer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import com.xjyzs.filetransfer.R
import com.xjyzs.filetransfer.utils.PickedFile
import com.xjyzs.filetransfer.utils.Request
import com.xjyzs.filetransfer.utils.Result
import com.xjyzs.filetransfer.utils.SafUtils
import com.xjyzs.filetransfer.utils.SharedState
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLConnection
import java.net.URLEncoder


/** 目录:JSON
 * 文件:打开流的函数 */
private sealed interface Resolved {
    data class Dir(val jsonEntries: List<String>) : Resolved

    data class File(
        val name: String, val size: Long?, val mimeType: String?,
        val asAttachment: Boolean = false, val open: () -> InputStream
    ) : Resolved
}

/** 生成JSON */
private fun entryJson(name: String, size: Long, link: String? = null): String {
    val n = name.replace("\\", "\\\\").replace("\"", "\\\"")
    val l = link?.replace("\\", "\\\\")?.replace("\"", "\\\"")
    return if (l == null) "[\"$n\",$size]" else "[\"$n\",$size,\"$l\"]"
}

// 选中内容时，隐藏上传表单
private val HIDE_UPLOAD_HTML =
    """<script>document.getElementById('uploadForm').style.display='none';</script>"""

class ServerService : Service() {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        startServer()

        return START_STICKY
    }

    private fun startServer() {
        val loc = Environment.getExternalStorageDirectory().path
        val appContext = applicationContext
        val wifiManager = this.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL, "FireTransfer:WifiLock"
        )
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
        serviceScope.launch {
            try {
                // 测试 IP 与端口是否可用
                val inetAddress = InetAddress.getByName(SharedState.host.value)
                ServerSocket(SharedState.port.value.toInt(), 1, inetAddress).use {}

                // 启动服务器
                server = embeddedServer(
                    CIO, port = SharedState.port.value.toInt(), host = SharedState.host.value
                ) {
                    routing {
                        route("/{pth...}") {
                            get { handleGet(call, loc, appContext) }
                            post { handlePost(call, loc) }
                            options { handleOptions(call, loc, appContext) }
                        }
                    }
                }
                server?.start(wait = false)
                SharedState._serverRunning.value = true
            } catch (e: Exception) {
                SharedState._errorMsg.value = e.message.toString()
                SharedState._errorDialogExpanded.value = true
            }
        }
    }


    private fun pathSegments(call: RoutingCall): List<String> {
        val pthLst: List<String>? = call.parameters.getAll("pth")
        return (pthLst ?: emptyList()).flatMap { it.split('/') }.filter { it.isNotEmpty() }
    }

    private fun resolve(loc: String, segments: List<String>, appContext: Context): Resolved? {
        if (SharedState.selectedFiles.isEmpty()) return resolveSdcard(loc, segments)
        return resolveSelected(segments, appContext)
    }

    private fun resolveSdcard(loc: String, segments: List<String>): Resolved? {
        val f = File(loc, segments.joinToString("/"))
        if (f.isFile) {
            return Resolved.File(
                f.name, f.length(), URLConnection.guessContentTypeFromName(f.name)
            ) {
                f.inputStream()
            }
        }
        if (f.isDirectory) {
            return Resolved.Dir(
                f.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { entryJson(it.name, if (it.isDirectory) -1 else it.length()) }
                ?: emptyList())
        }
        return null
    }

    private fun resolveSelected(segments: List<String>, appContext: Context): Resolved? {
        val selected = SharedState.selectedFiles
        if (segments.isEmpty()) {
            val nameCount = selected.groupingBy { it.name }.eachCount()
            return Resolved.Dir(
                selected.mapIndexed { i, item ->
                    val link = if (nameCount[item.name] == 1) {
                        item.name
                    } else {
                        "${item.name} (${selected.take(i).count { it.name == item.name } + 1})"
                    }
                    entryJson(item.name, if (item.isDirectory) -1 else (item.size ?: 0), link)
                })
        }
        val item = resolveRootItem(segments[0], selected) ?: return null

        if (!item.isDirectory) {
            if (segments.size > 1) return null
            return Resolved.File(item.name, item.size, item.mimeType, asAttachment = true) {
                appContext.contentResolver.openInputStream(item.uri)
                    ?: throw IOException("无法打开 ${item.name}")
            }
        }

        val treeUri = item.uri
        var docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return null
        }
        val rest = segments.drop(1)
        if (rest.isEmpty()) return Resolved.Dir(treeEntries(appContext, treeUri, docId))
        for (seg in rest) {
            val child = SafUtils.findChild(appContext, treeUri, docId, seg) ?: return null
            if (child.isDirectory) {
                docId = child.docId
            } else {
                if (seg != rest.last()) return null
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
                return Resolved.File(child.name, child.size, child.mimeType, asAttachment = true) {
                    appContext.contentResolver.openInputStream(docUri)
                        ?: throw IOException("无法打开 ${child.name}")
                }
            }
        }
        return Resolved.Dir(treeEntries(appContext, treeUri, docId))
    }

    private fun resolveRootItem(seg: String, selected: List<PickedFile>): PickedFile? {
        selected.firstOrNull { it.name == seg }?.let { return it }
        val m = Regex("^(.*) \\((\\d+)\\)$").find(seg) ?: return null
        val base = m.groupValues[1]
        val occurrence = m.groupValues[2].toIntOrNull() ?: return null
        var count = 0
        for (item in selected) {
            if (item.name == base) {
                count++
                if (count == occurrence) return item
            }
        }
        return null
    }

    private fun treeEntries(appContext: Context, treeUri: Uri, docId: String): List<String> =
        SafUtils.listChildren(appContext, treeUri, docId).map {
            entryJson(it.name, if (it.isDirectory) -1 else (it.size ?: 0))
        }

    private suspend fun handleGet(call: RoutingCall, loc: String, appContext: Context) {
        val segments = pathSegments(call)
        val ref = resolve(loc, segments, appContext)
        val selectedMode = SharedState.selectedFiles.isNotEmpty()
        when (ref) {
            null, is Resolved.Dir -> call.respondText(
                htmlHead + "File Explorer" + html + if (selectedMode) HIDE_UPLOAD_HTML else "",
                ContentType.Text.Html
            )

            is Resolved.File -> {
                SharedState.requestLogs.add(
                    Request(
                        call.request.origin.remoteAddress,
                        System.currentTimeMillis(),
                        call.request.uri,
                        Result.GET_FILE
                    )
                )
                sendFile(call, ref)
            }
        }
    }

    private suspend fun handlePost(call: RoutingCall, loc: String) {
        val pthLst: List<String>? = call.parameters.getAll("pth")
        val pth = pthLst?.joinToString("/") ?: ""
        SharedState.requestLogs.add(
            Request(
                call.request.origin.remoteAddress,
                System.currentTimeMillis(),
                pth,
                Result.UPLOAD_FILE
            )
        )
        saveFile(call, "$loc/$pth")
        call.respondRedirect(call.request.uri)
    }

    private suspend fun handleOptions(call: RoutingCall, loc: String, appContext: Context) {
        val segments = pathSegments(call)
        val ref = resolve(loc, segments, appContext)
        SharedState.requestLogs.add(
            Request(
                call.request.origin.remoteAddress,
                System.currentTimeMillis(),
                call.request.uri,
                Result.LIST_DIR
            )
        )
        when (ref) {
            null -> call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
            is Resolved.Dir -> call.respondText(
                "[" + ref.jsonEntries.joinToString(",") + "]", ContentType.Application.Json
            )

            is Resolved.File -> call.respondText(
                entryJson(ref.name, ref.size ?: 0), ContentType.Application.Json
            )
        }
    }

    private fun contentDispositionHeader(name: String): String {
        val quoted = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "filename=\"$quoted\"; filename*=UTF-8''$encoded"
    }

    private suspend fun sendFile(call: RoutingCall, ref: Resolved.File) {
        val contentType = ref.mimeType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
            ?: ContentType.Application.OctetStream
        val size = ref.size

        if (ref.asAttachment) {
            call.response.headers.append(
                HttpHeaders.ContentDisposition, contentDispositionHeader(ref.name)
            )
        }
        if (size != null) {
            call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
            val range = parseRange(call.request.headers[HttpHeaders.Range], size)
            if (range != null) {
                val length = range.last - range.first + 1
                call.response.headers.append(
                    HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/$size"
                )
                call.respondBytesWriter(contentType, HttpStatusCode.PartialContent, length) {
                    streamFrom(ref.open, range.first, length)
                }
                return
            }
        }
        call.respondBytesWriter(contentType, HttpStatusCode.OK, size) {
            streamFrom(ref.open, 0, size)
        }
    }

    private suspend fun ByteWriteChannel.streamFrom(
        open: () -> InputStream, start: Long, length: Long?
    ) {
        open().use { input ->
            var skip = start
            while (skip > 0) {
                val n = input.skip(skip)
                if (n > 0) skip -= n
                else if (input.read() == -1) return
                else skip--
            }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var left = length
            while (left == null || left > 0) {
                val want =
                    if (left == null) buffer.size else minOf(buffer.size.toLong(), left).toInt()
                val n = input.read(buffer, 0, want)
                if (n <= 0) break
                writeFully(buffer, 0, n)
                if (left != null) left -= n
            }
        }
    }

    private fun parseRange(header: String?, total: Long): LongRange? {
        if (header == null) return null
        val m = Regex("""^bytes=(\d*)-(\d*)$""").find(header.trim()) ?: return null
        val startStr = m.groupValues[1]
        val endStr = m.groupValues[2]
        return if (startStr.isEmpty()) {
            val suffix = endStr.toLongOrNull() ?: return null
            if (suffix <= 0) null else (total - suffix).coerceAtLeast(0)..<total
        } else {
            val start = startStr.toLongOrNull() ?: return null
            if (start >= total) return null
            val end = if (endStr.isEmpty()) {
                total - 1
            } else {
                (endStr.toLongOrNull() ?: return null).coerceAtMost(total - 1)
            }
            if (end < start) null else start..end
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "server"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "服务器", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(R.string.app_name.toString())
            .setContentText("端口: ${SharedState.port.value}")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        serviceScope.launch {
            server?.stop(1000, 2000)
            SharedState._serverRunning.value = false
            server = null
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

suspend fun saveFile(call: RoutingCall, destinationDir: String) {
    val multipartData = call.receiveMultipart()
    multipartData.forEachPart { part ->
        if (part is PartData.FileItem) {
            val originalFileName = part.originalFileName ?: "上传文件"
            val targetFile = File(destinationDir, originalFileName)
            part.streamProvider().use { inputStream ->
                targetFile.outputStream().buffered().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        part.dispose()
    }
}

val htmlHead = """<!DOCTYPE html>
<meta charset="UTF-8">
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="icon" type="image/svg+xml"
href='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22256%22 height=%22256%22 viewBox=%220 0 256 256%22%3E%3Cpath d=%22M207,48c11.35.07,29.02-1.54,39.44.06,2.88.44,7.33,2.72,7.59,5.94l.98-1h1v183c-2.49,3.85-4.54,7.16-9.56,7.94-8.44,1.3-22.26-.02-31.44.06.22-2.52,1.18-4.93,1.1-7.52-.06-2.09-1.1-4.38-1.1-4.98v-18c0-1.58-2.06-5.79-1-8.51h-2c-.91-17.32-8.59-28.38-25.94-32.55l-116.32-.2c-10.52,2.56-19.51,9.22-23.5,19.5-5.55,14.27-.5,36.71-2.24,52.26-10-.08-25.24,1.35-34.44-.06-4.75-.73-6.53-3.91-9.56-6.94v-103c7.49-1.94,14.85-6.94,21.67-10.83,40.61-23.14,81.13-46.71,121.04-70.96l62.62-.17,1.67-4.04h0Z%22 fill=%22%23fecb3d%22/%3E%3Cpath d=%22M207,48l-1.67,4.04-62.62.17c-39.91,24.25-80.43,47.82-121.04,70.96-6.82,3.89-14.18,8.89-21.67,10.83v-67c2.46-2.72,4.54-6.36,8.56-6.94,32.03-3.54,70.09,9.29,97-12,33.81.06,67.65-.26,101.45-.05h0Z%22 fill=%22%23fed45b%22/%3E%3Cpath d=%22M255,53l-.98,1c-.25-3.22-4.71-5.49-7.59-5.94-10.41-1.61-28.09,0-39.44-.06-33.8-.21-67.64.11-101.45.05-26.9,21.3-64.97,8.46-97,12-4.02.58-6.09,4.22-8.56,6.94V28c2.5-3.95,4.52-7.03,9.56-7.94,21.29,1.57,46.08-2.32,66.94-.07,14.67,1.58,19.34,15.71,28.99,24.01l140.03-.03c4.71.61,9.71,3.63,9.48,9.02h.02Z%22 fill=%22%23de9e01%22/%3E%3Cpath d=%22M212,205c.68,12.88-.5,26.09,0,39-55.94.42-112.06.43-168,0,1.74-15.55-3.31-37.99,2.24-52.26,3.99-10.28,12.98-16.94,23.5-19.5l116.32.2c17.35,4.17,25.03,15.22,25.94,32.56ZM77,204c-1.36,1.94-1.05,8,1.5,8h99c.73,0,3.08-3.17,2.55-4.45.06-1.04-2.05-3.55-2.55-3.55h-100.5Z%22 fill=%22%230b7cca%22/%3E%3Cpath d=%22M212,205h2c-1.06,2.71,1,6.92,1,8.5v18c0,.61,1.04,2.9,1.1,4.98.08,2.59-.89,4.99-1.1,7.52h-3c-.5-12.91.68-26.12,0-39Z%22 fill=%22%23de9e01%22/%3E%3Cpath d=%22M77,204h100.5c.5,0,2.61,2.51,2.55,3.55.53,1.28-1.82,4.45-2.55,4.45h-99c-2.55,0-2.86-6.06-1.5-8Z%22 fill=%22%23114a8b%22/%3E%3C/svg%3E'>
<style>html::before {
    content: "";
    width: 100%;
    height: 100%;
    position: fixed;
    z-index: -1;
    background-image: linear-gradient(120deg, #e0fffc 0%, #f0ffdf 100%);
}

body {
    background-color: #F1FCF3;
}

a {
    color: #597A6C;
    font-size: 24px;
    text-decoration: none;
    word-break: break-all;
    margin: 10px
}

a:hover {
    color: #2B3B34;
}

p {
    font-size: 24px;
    word-break: break-all;
    margin: 10px;
}

#topBar {
    position: sticky;
    top: 6px;
    z-index: 1000;
}

.pth {
    background-color: #CACACA4C;
    backdrop-filter: blur(4px);
    transition: transform 0.3s, background-color 0.5s;
}


.circle {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    user-select: none;
}

.circle:hover,#pth1:hover {
    background-color: #9696964C;
    transform: translateY(-1px);
}

#pth1:hover{
    transform: translateY(-1px)scale(1.003);
}

#pth1 {
    border-radius: 50px;
    padding-left: 15px;
    padding-right: 15px;
    flex: 1;
    overflow: hidden;
    direction: rtl;
    text-align: left;
}

#grid span {
    color: #4C4C4C99;
    font-size: 24px;
    margin: 10px;
}

.btn, input[type="file"]::file-selector-button {
    background-color: #6464644C;
    border: none;
    border-radius: 8px;
    backdrop-filter: blur(4px);
}</style>
</head><title>""".replace("    ", "").replace("\n", "")

val html = """</title>
<p style="font-size: 42px;">File Explorer</p>
<div id="topBar">
    <div style="display: flex;gap: 10px;">
        <div class="pth circle" id="back"><p style="font-size:16px;color:black">↑</p></div>
        <div class="pth" id="pth1"><span style="white-space: nowrap;direction: ltr;display: inline-block; font-size: 20px;"
                id="pthContent"></span></div>
        <div class="pth circle"><a href="/nav" style="font-size:16px;color:black">⋯</a></div>
    </div>
    <form id="uploadForm" action="." method="post" enctype="multipart/form-data">
        <div style="display: flex;justify-content: flex-end;padding-top: 6px">
            <div style="display: flex;flex-direction: column;">
                <div style="display: flex; gap: 10px;">
                    <input type="file" id="file" name="file" required>
                    <button type="submit" class="btn">上传</button>
                </div>
                <div id="progressContainer" style="width: 100%;display:none;">
                    <div style="display: flex;justify-content: space-between">
                        <span id="speedText" style="font-size: 14px;"></span>
                        <span id="progressText" style="font-size: 14px"></span>
                    </div>
                    <div id="progressBar"
                         style="width:0; height:2px; background: #6464644C;border-radius: 1px"></div>
                </div>
            </div>
        </div>
    </form>
</div>
<div id="grid" style="display: grid;grid-template-columns: 1fr 0.5fr;line-height: 24px;"></div>
<script>
    function numToSize(n) {
        if (n < 1024) {
            return n + "B";
        } else if (n < 1048576) {
            return (n / 1024).toFixed(2) + "KB";
        } else if (n < 1073741824) {
            return (n / 1048576).toFixed(2) + "MB";
        } else {
            return (n / 1073741824).toFixed(2) + "GB";
        }
    }

    async function send() {
        document.getElementById("pthContent").textContent = decodeURIComponent(window.location.pathname).replace('files/', '')
        const res = await fetch(window.location, {method: 'OPTIONS'});
        const resClone = res.clone();
        const files = await res.json().catch(async () => {
        const text = await resClone.text();
        alert(text);return;
        });
        const grid = document.getElementById('grid');
        grid.innerHTML = "";
        for (const entry of files) {
            const name = entry[0];
            const size = entry[1];
            const link = entry[2] ?? name;
            const isDir = size === -1;
            let displaySize = ''
            if (!isDir) {
                displaySize = numToSize(size);
            }
            const a = document.createElement("a");
            a.textContent = name;
            a.href = link;
            if (isDir) {
                a.addEventListener("click", (e) => {
                    e.preventDefault();
                    const newUrl = link + "/";
                    history.pushState({}, "", newUrl);
                    send();
                });
            }
            const span = document.createElement("span");
            span.textContent = isDir ? "-" : displaySize;
            grid.appendChild(a);
            grid.appendChild(span);
        }
    }

    document.getElementById("back").addEventListener("click", () => {
        let path = location.pathname;
        if (!path.endsWith("/")) path += "/";
        const parts = path.split("/").filter(x => x.length > 0);
        parts.pop();
        const newPath = "/" + (parts.length ? parts.join("/") + "/" : "");
        history.pushState({}, "", newPath);
        send();
    });
    window.addEventListener("popstate", () => {
        send();
    });
    const form = document.getElementById('uploadForm');
    const fileInput = document.getElementById('file');
    const progressContainer = document.getElementById('progressContainer');
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    const speedText = document.getElementById('speedText');
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const file = fileInput.files[0];
        const formData = new FormData();
        formData.append('file', file);
        const xhr = new XMLHttpRequest();
        xhr.open('POST', form.action, true);
        let lastLoaded = 0;
        let lastTime = Date.now();
        xhr.upload.addEventListener('progress', function (e) {
            if (e.lengthComputable) {
                let currentTime = Date.now();
                const percent = (e.loaded / e.total * 100).toFixed(1);
                progressBar.style.width = percent + '%';
                progressText.textContent = percent + '%';
                speedText.textContent = numToSize((e.loaded - lastLoaded) / (currentTime - lastTime) * 1000) + '/s';
                lastTime = currentTime;
                lastLoaded = e.loaded;
                progressContainer.style.display = 'block';
            }
        });
        xhr.onload = function () {
            if (xhr.status === 200) {
                progressText.textContent = 'success';
                progressBar.style.width = '100%';
            } else {
                progressText.textContent = 'failure';
            }
            send();
        };

        xhr.send(formData);
    });
    document.addEventListener('DOMContentLoaded', function () {
        send();
    })</script>"""