package com.xjyzs.filetransfer.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.xjyzs.filetransfer.ui.animation.predictiveback.ScalePredictiveBackAnimation
import com.xjyzs.filetransfer.ui.navigation.rememberNavigator
import com.xjyzs.filetransfer.ui.screens.LogScreenContent
import com.xjyzs.filetransfer.ui.screens.MainScreenContent
import com.xjyzs.filetransfer.ui.theme.FileTransferTheme
import com.xjyzs.filetransfer.ui.viewmodel.PredictiveBackExitDirection
import com.xjyzs.filetransfer.utils.PickedFile
import com.xjyzs.filetransfer.utils.SafUtils
import com.xjyzs.filetransfer.utils.SharedState
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleFileIntent(intent)
        enableEdgeToEdge()
        setContent {
            FileTransferTheme {
                Surface {
                    AppNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFileIntent(intent)
    }

    private fun handleFileIntent(intent: Intent?) {
        for (uri in extractFileUris(intent)) {
            if (SharedState.selectedFiles.any { it.uri == uri }) continue
            SafUtils.takePersistableReadPermission(this, uri)
            val info = SafUtils.queryInfo(this, uri)
            SharedState.selectedFiles.add(
                PickedFile(
                    uri = uri,
                    name = info?.name ?: uri.lastPathSegment ?: "打开文件",
                    isDirectory = false,
                    mimeType = info?.mimeType ?: runCatching { contentResolver.getType(uri) }.getOrNull(),
                    size = info?.size
                )
            )
        }
    }

    private fun extractFileUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val action = intent.action

        // ACTION_VIEW
        if (action == Intent.ACTION_VIEW) return listOfNotNull(intent.data)

        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()

        // ACTION_SEND
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (stream != null) return listOf(stream)

        // ACTION_SEND_MULTIPLE
        val streamList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (!streamList.isNullOrEmpty()) return streamList.filterNotNull()

        val streamString = intent.getStringExtra(Intent.EXTRA_STREAM)
        if (!streamString.isNullOrEmpty()) {
            val uri = streamString.toUri()
            if (uri.scheme != null) return listOf(uri)
        }

        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (uris.isNotEmpty()) return uris
        }
        return emptyList()
    }
}

@Parcelize
data object MainScreen : NavKey, Parcelable

@Parcelize
data object LogScreen : NavKey, Parcelable

@Composable
fun AppNav() {
    val navigator = rememberNavigator(MainScreen) // 起始页
    val predictiveBackHandler =
        remember { ScalePredictiveBackAnimation(PredictiveBackExitDirection.FOLLOW_GESTURE) }

    var gestureState: NavigationEventState<SceneInfo<NavKey>>? = null
    val scope = rememberCoroutineScope()

    val onBack: (() -> Unit) -> Unit = { callback ->
        scope.launch {
            predictiveBackHandler.onBackPressed(
                transitionState = gestureState?.transitionState,
                currentPageKey = navigator.current()
            )
            callback()
            navigator.pop()
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = navigator.backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            NavEntryDecorator(
                onPop = { key -> predictiveBackHandler.onPagePop(key, scope) }) { content ->
                with(predictiveBackHandler) {
                    Box(
                        Modifier.fillMaxSize().predictiveBackAnimationDecorator(
                                gestureState?.transitionState,
                                content.contentKey,
                                navigator.current()
                            )
                    ) { content.Content() }
                }
            }),
        entryProvider = entryProvider {
            entry<MainScreen> { MainScreenContent(onNavigateToLog = { navigator.push(LogScreen) }) }
            entry<LogScreen> { LogScreenContent(onBack = { navigator.pop() }) }
        },
    )

    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
        sceneDecoratorStrategies = emptyList(),
        sharedTransitionScope = null,
        onBack = { onBack {} },
    )

    gestureState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) })

    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = navigator.backStack.size > 1,
        onBackCompleted = { cb -> onBack(cb) },
        onBackCancelled = { cb -> cb() })

    NavDisplay(
        sceneState = sceneState,
        navigationEventState = gestureState,
        sizeTransform = null,
        predictivePopTransitionSpec = { swipeEdge ->
            with(predictiveBackHandler) { onPredictivePopTransitionSpec(swipeEdge) }
        },
        popTransitionSpec = { with(predictiveBackHandler) { onPopTransitionSpec() } },
        transitionSpec = { with(predictiveBackHandler) { onTransitionSpec() } })
}