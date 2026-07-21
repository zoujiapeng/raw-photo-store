package com.zoujiapeng.rawjudge.data

import android.content.Context
import com.zoujiapeng.rawjudge.domain.AppUiState
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class LocalStateStore(context: Context) {
    private val file = context.filesDir.resolve("rawjudge-state-v1.bin")

    fun load(): AppUiState? = runCatching {
        if (!file.isFile) return null
        ObjectInputStream(file.inputStream().buffered()).use { stream ->
            (stream.readObject() as? AppUiState)?.copy(toastMessage = null)
        }
    }.getOrNull()

    fun save(state: AppUiState) {
        val temporary = file.resolveSibling("${file.name}.tmp")
        ObjectOutputStream(temporary.outputStream().buffered()).use { stream ->
            stream.writeObject(state.copy(toastMessage = null))
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
