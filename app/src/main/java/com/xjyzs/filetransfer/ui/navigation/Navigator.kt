package com.xjyzs.filetransfer.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Simple navigation helper that owns a back stack.
 * Supports push/pop/current, and is saved/restored with [Navigator.Saver].
 *
 * Ported from the reference project's Navigator to drive the predictive back
 * animation wiring (rememberDecoratedNavEntries + rememberSceneState + NavDisplay).
 */
class Navigator(
    initialKey: NavKey
) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    /**
     * Push a key onto the back stack.
     */
    fun push(key: NavKey) {
        if (backStack.lastOrNull() == key) {
            Log.i("Navigator", "Trying push current page to backStack again, ignore!")
            return
        }

        backStack.add(key)
    }

    private var lastPopTime = 0L

    /**
     * Pop the top key if present.
     */
    fun pop() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPopTime < 100) {
            Log.i("Navigator", "pop call more than 1 times in 100ms, ignore!")
            return
        }

        if (backStackSize() <= 1) return
        backStack.removeLastOrNull()
    }

    /**
     * Get current NavKey on the back stack.
     */
    fun current(): NavKey? {
        return backStack.lastOrNull()
    }

    /**
     * Get current size of back stack.
     */
    fun backStackSize(): Int {
        return backStack.size
    }

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(save = { navigator ->
            navigator.backStack.toList()
        }, restore = { savedList ->
            val navigator = Navigator(savedList.firstOrNull() ?: error("Empty back stack"))
            navigator.backStack.clear()
            navigator.backStack.addAll(savedList)
            navigator
        })
    }
}

@Composable
fun rememberNavigator(startKey: NavKey): Navigator {
    return rememberSaveable(startKey, saver = Navigator.Saver) {
        Navigator(startKey)
    }
}
