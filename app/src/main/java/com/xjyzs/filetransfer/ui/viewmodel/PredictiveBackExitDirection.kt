package com.xjyzs.filetransfer.ui.viewmodel

/**
 * Predictive back exit direction (ported from the reference project).
 */
enum class PredictiveBackExitDirection(val value: String) {
    FOLLOW_GESTURE("follow_gesture"),
    ALWAYS_RIGHT("always_right"),
    ALWAYS_LEFT("always_left");

    companion object {
        fun fromValueOrDefault(value: String) =
            entries.find { it.value == value } ?: FOLLOW_GESTURE
    }
}
