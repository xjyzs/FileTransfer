package com.xjyzs.filetransfer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.xjyzs.filetransfer.ui.theme.FileTransferTheme
import java.net.NetworkInterface
import kotlin.concurrent.thread
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.getString

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileTransferTheme {
                Surface {
                    MainUI()
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun MainUI() {
    val context = LocalContext.current
    val logsStr = stringResource(R.string.logs)
    val msgs = remember { mutableStateListOf(logsStr) }
    val ipLst = remember { mutableStateListOf<String>() }
    var ip by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var ipExpanded by remember { mutableStateOf(false) }
    var qrExpanded by remember { mutableStateOf(false) }
    var qrTxt by remember { mutableStateOf("") }
    val ipExpandedRotation by animateFloatAsState(
        targetValue = if (ipExpanded) 180f else 0f, animationSpec = tween(
            durationMillis = 300, easing = FastOutSlowInEasing
        )
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
                (context as ComponentActivity).finish()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    context as Activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001
                )
            }
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        }
    }
    LaunchedEffect(Unit) {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress) {
                    val addrStr = addr.hostAddress ?: "unknown"
                    if (!addrStr.contains("fe80")) {
                        ipLst.add(addrStr)
                        if ("192." in addrStr) {
                            ip = addrStr
                        }
                    }
                }
            }
        }
        ipLst.add("127.0.0.1")
        if (ip.isEmpty()) {
            ip = "127.0.0.1"
        }
    }
    LaunchedEffect(Unit) {
        thread {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            val pyLog = py.getModule("log")
            pyLog.callAttr("start")
            while (true) {
                try {
                    val tmp = pyLog.callAttr("poll").toString()
                    if (tmp.isNotEmpty()) {
                        msgs.add(tmp)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(100)
            }
        }
    }
    @Composable
    fun IpRow(innerIp: String, expandButton: Boolean = false) {
        Row {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp, horizontal = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        ip = innerIp
                        thread {
                            if (!Python.isStarted()) {
                                Python.start(AndroidPlatform(context))
                            }
                            val py = Python.getInstance()
                            val server = py.getModule("server")
                            val builtins = py.builtins
                            val dict = builtins.callAttr("dict")
                            dict.callAttr(
                                "__setitem__", "uploadSuccessful", getString(
                                    context, R.string.uploadSuccessful
                                )
                            )
                            dict.callAttr(
                                "__setitem__", "uploadFailed", getString(
                                    context, R.string.uploadFailed
                                )
                            )
                            dict.callAttr(
                                "__setitem__", "serverUrl", getString(
                                    context, R.string.serverUrl
                                )
                            )
                            try {
                                server.callAttr(
                                    "main", ip, 1145, "http://IP地址", dict
                                )
                            } catch (e: Throwable) {
                                errorMsg = e.stackTraceToString()
                                isError = true
                            }
                        }
                    }
                    .background(
                        if (innerIp == ip) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }, RoundedCornerShape(6.dp)
                    )) {
                Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            innerIp, color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton({
                        qrExpanded = !qrExpanded
                        qrTxt =
                            if (':' in innerIp) "http://[$innerIp]:1145" else "http://${innerIp}:1145"
                    }, Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (expandButton) {
                        IconButton({ ipExpanded = !ipExpanded }, Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.rotate(ipExpandedRotation)
                            )
                        }
                    }
                }
            }
        }
    }
    if (isError) {
        AlertDialog(
            { isError = false },
            { TextButton({ isError = false }) { Text("确定") } },
            title = { Text(stringResource(R.string.error)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            errorMsg
                        )
                    }
                }
            })
    }
    if (qrExpanded) {
        Dialog({ qrExpanded = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface
            ) {
                val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                    qrTxt, BarcodeFormat.QR_CODE, 512, 512, mapOf(
                        EncodeHintType.MARGIN to 0
                    )
                )
                val bmp = createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) {
                    for (y in 0 until 512) {
                        bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                Column(
                    Modifier.padding(vertical = 30.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.size(10.dp))
                    TextButton({
                        val intent = Intent(Intent.ACTION_VIEW, qrTxt.toUri())
                        context.startActivity(intent)
                    }) {
                        Text(qrTxt, fontSize = 18.sp)
                    }
                }
            }
        }
    }
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Column(Modifier.animateContentSize()) {
                Crossfade(ipExpanded) { ipExpanded ->
                    if (!ipExpanded) {
                        IpRow(ip, expandButton = true)
                    } else {
                        IpRow(ipLst[0], expandButton = true)
                    }
                }
                if (ipExpanded) {
                    var flag = true
                    for (innerIp in ipLst) {
                        if (flag) {
                            flag = false
                        } else {
                            IpRow(innerIp)
                        }
                    }
                }
            }
            SelectionContainer(
                Modifier.weight(1f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn {
                        itemsIndexed(msgs) { _, msg ->
                            StringDisplay(msg)
                        }
                    }
                }
            }
        }
    }
}