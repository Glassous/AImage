package com.glassous.aimage.oss

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AutoSyncType {
    DownloadOnStart,
    UploadModelConfig,
    UploadHistoryAdd,
    UploadHistoryDelete
}

data class AutoSyncEvent(
    val type: AutoSyncType,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object AutoSyncNotifier {
    private val _events: MutableStateFlow<AutoSyncEvent?> = MutableStateFlow(null)
    val events: StateFlow<AutoSyncEvent?> = _events

    fun report(event: AutoSyncEvent) {
        _events.value = event
    }
}