package com.xjyzs.filetransfer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.xjyzs.filetransfer.ui.util.LogBadge
import com.xjyzs.filetransfer.utils.SharedState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogScreenContent(onBack: () -> Unit) {
    val requestLogs = remember { SharedState.requestLogs }

    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainer, topBar = {

        LargeFlexibleTopAppBar(
            title = { Text("访问日志") }, navigationIcon = {
            IconButton(
                {
                    onBack()
                }, colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.1f
                    )
                )
            ) { Icon(Icons.Default.ArrowBackIosNew, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ), scrollBehavior = scrollBehavior
        )
    }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
        ) {
            if (requestLogs.isEmpty()) Text(
                "等待访问...", color = MaterialTheme.colorScheme.secondary
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(requestLogs.asReversed()) { _, log ->
                    FlowRow(Modifier.padding(vertical = 3.dp)) {
                        LogBadge(
                            text = Instant.ofEpochMilli(log.time).atZone(ZoneId.systemDefault())
                                .format(formatter),
                            backgroundColor = Color(0xFFEAEAEA),
                            textColor = Color(0xFF5D5D5D)
                        )
                        Spacer(Modifier.size(4.dp))
                        LogBadge(
                            text = log.ip,
                            backgroundColor = Color(0xFFE3F2FD),
                            textColor = Color(0xFF1565C0)
                        )
                        Spacer(Modifier.size(4.dp))
                        LogBadge(
                            text = log.pth,
                            backgroundColor = Color(0xFFEDE7F6),
                            textColor = Color(0xFF5E35B1)
                        )
                        Spacer(Modifier.size(4.dp))
                        LogBadge(
                            text = log.result.toString(),
                            backgroundColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}