package com.zoujiapeng.rawjudge.data

import android.content.Context
import com.zoujiapeng.rawjudge.domain.AppUiState
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class LocalStateStore(context: Context) {
    private val file = context.filesDir.resolve("rawjudge-state-v3.bin")

    fun load(): AppUiState? = runCatching {
        if (!file.isFile) return null
        ObjectInputStream(file.inputStream().buffered()).use { stream ->
            (stream.readObject() as? AppUiState)?.copy(
                isLoading = false,
                isOnline = false,
                uploadVisible = false,
                blindVisible = false,
                profileVisible = false,
                blindPair = null,
                toastMessage = "当前显示上次缓存；联网后将自动同步。"
            )
        }
    }.getOrNull()

    fun save(state: AppUiState) {
        val stable = state.copy(
            isLoading = false,
            isOnline = false,
            uploadVisible = false,
            blindVisible = false,
            profileVisible = false,
            blindPair = null,
            toastMessage = null
        )
        val temporary = file.resolveSibling("${file.name}.tmp")
        ObjectOutputStream(temporary.outputStream().buffered()).use { stream ->
            stream.writeObject(stable)
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
