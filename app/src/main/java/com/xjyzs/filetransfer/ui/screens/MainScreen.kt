package com.xjyzs.filetransfer.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.xjyzs.filetransfer.R
import com.xjyzs.filetransfer.service.ServerService
import com.xjyzs.filetransfer.ui.util.ColumnCard
import com.xjyzs.filetransfer.ui.util.SmallTextField
import com.xjyzs.filetransfer.utils.PickedFile
import com.xjyzs.filetransfer.utils.SafUtils
import com.xjyzs.filetransfer.utils.SharedState

@SuppressLint("BatteryLife")
@Composable
fun MainScreenContent(onNavigateToLog: () -> Unit) {
    val context = LocalContext.current

    // 多选
    val pickFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        for (uri in uris) {
            if (SharedState.selectedFiles.any { it.uri == uri }) continue
            SafUtils.takePersistableReadPermission(context, uri)
            val info = SafUtils.queryInfo(context, uri)
            SharedState.selectedFiles.add(
                PickedFile(
                    uri = uri,
                    name = info.name,
                    isDirectory = false,
                    mimeType = info.mimeType,
                    size = info.size
                )
            )
        }
    }

    // 目录
    val pickDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && SharedState.selectedFiles.none { it.uri == uri }) {
            SafUtils.takePersistableReadPermission(context, uri)
            val info = SafUtils.queryTreeRootInfo(context, uri)
            SharedState.selectedFiles.add(
                PickedFile(
                    uri = uri,
                    name = info?.name ?: uri.lastPathSegment ?: "目录",
                    isDirectory = true,
                    mimeType = info?.mimeType,
                    size = null
                )
            )
        }
    }
    val ipLst by SharedState.ipLst.collectAsStateWithLifecycle()
    val host by SharedState.host.collectAsStateWithLifecycle()
    val port by SharedState.port.collectAsStateWithLifecycle()
    val enablePreview by SharedState.enablePreview.collectAsStateWithLifecycle()
    val errorDialogExpanded by SharedState.errorDialogExpanded.collectAsStateWithLifecycle()
    val errorMsg by SharedState.errorMsg.collectAsStateWithLifecycle()
    val serverRunning by SharedState.serverRunning.collectAsStateWithLifecycle()

    var ipExpanded by remember { mutableStateOf(false) }
    val ipExpandedRotation by animateFloatAsState(
        targetValue = if (ipExpanded) 180f else 0f, animationSpec = tween(
            durationMillis = 300, easing = FastOutSlowInEasing
        )
    )

    var qrExpanded by remember { mutableStateOf(false) }

    var selectedFilesExpanded by remember { mutableStateOf(false) }
    val selectedFilesExpandedRotation by animateFloatAsState(
        targetValue = if (selectedFilesExpanded) 180f else 0f, animationSpec = tween(
            durationMillis = 300, easing = FastOutSlowInEasing
        )
    )
    var qrTxt by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
                (context as ComponentActivity).finish()
                return@LaunchedEffect
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
        val justInitialized = SharedState.ensureInitialized(context)
        if (justInitialized) {
            ServerService.startIfNotRunning(context)
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        }
    }
    @Composable
    fun IpRow(
        innerIp: String, expandButton: Boolean = false, highlightCurrentHost: Boolean = false
    ) {
        Row(Modifier.padding(vertical = 4.dp)) {
            Box(modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    SharedState._host.value = innerIp
                    ipExpanded = false
                    ServerService.restart(context)
                }
                .background(
                    if (highlightCurrentHost && innerIp == host) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }, RoundedCornerShape(6.dp)
                )) {
                Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            innerIp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
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
    if (errorDialogExpanded) {
        AlertDialog(
            { SharedState._errorDialogExpanded.value = false },
            { TextButton({ SharedState._errorDialogExpanded.value = false }) { Text("确定") } },
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
                val bitMatrix: BitMatrix = QRCodeWriter().encode(
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainer, topBar = {
        LargeFlexibleTopAppBar(
            title = { Text(stringResource(R.string.app_name)) }, actions = {
            IconButton(
                {
                    onNavigateToLog()
                }, colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.1f
                    )
                )
            ) { Icon(Icons.Default.Terminal, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ), scrollBehavior = scrollBehavior
        )
    }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.height(IntrinsicSize.Max)) {
                ColumnCard(
                    Modifier
                        .weight(0.618f)
                        .fillMaxHeight()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("服务状态", fontSize = 22.sp)
                        IconButton({
                            qrExpanded = !qrExpanded
                            qrTxt =
                                if (':' in host) "http://[$host]:$port" else "http://${host}:$port"
                        }, Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(if (serverRunning) "运行中" else "未运行")
                    Spacer(Modifier.weight(1f))
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton({
                            ServerService.restart(context)
                        }) { Text(if (serverRunning) "重启" else "启动") }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    ColumnCard {
                        Text("IP", fontSize = 22.sp)
                        IpRow(host, expandButton = true)
                        DropdownMenu(ipExpanded, { ipExpanded = false }) {
                            for (innerIp in ipLst) IpRow(innerIp, highlightCurrentHost = true)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    ColumnCard {
                        Text("端口号", fontSize = 22.sp)
                        Row {
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                SmallTextField(
                                    port,
                                    { SharedState._port.value = it },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    ColumnCard {
                        Text("预览", fontSize = 22.sp)
                        Row {
                            Spacer(Modifier.weight(1f))
                            Switch(
                                enablePreview,
                                { SharedState._enablePreview.value = !enablePreview })
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            ColumnCard(verticalPadding = 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分享指定文件", fontSize = 22.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        {
                            pickDirLauncher.launch(null)
                        }, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.1f
                            )
                        )
                    ) { Icon(Icons.Default.Add, null) }
                    IconButton(
                        {
                            pickFilesLauncher.launch(arrayOf("*/*"))
                        }, colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.1f
                            )
                        )
                    ) { Icon(Icons.Default.FileOpen, null) }
                }
                Row(Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            selectedFilesExpanded = true
                        }
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)
                        )) {
                        Row(
                            Modifier.padding(
                                start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp
                            )
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    "已选择 ${SharedState.selectedFiles.size}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                { selectedFilesExpanded = !selectedFilesExpanded },
                                Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate(selectedFilesExpandedRotation)
                                )
                            }
                        }
                    }
                }
                DropdownMenu(selectedFilesExpanded, { selectedFilesExpanded = false }) {
                    if (SharedState.selectedFiles.isEmpty()) {
                        Text(
                            "未选择文件或目录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(Modifier.padding(top = 4.dp)) {
                            SharedState.selectedFiles.forEachIndexed { index, file ->
                                Row(
                                    Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (file.isDirectory) {
                                            Icons.Default.Folder
                                        } else {
                                            Icons.AutoMirrored.Filled.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        file.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp
                                    )
                                    if (!file.isDirectory && file.size != null) {
                                        Text(
                                            formatFileSize(file.size),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.size(6.dp))
                                    }
                                    IconButton(
                                        { SharedState.selectedFiles.removeAt(index) },
                                        Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String = when {
    size < 1024L -> "$size B"
    size < 1024L * 1024 -> String.format("%.2f KB", size / 1024.0)
    size < 1024L * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
    else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
}