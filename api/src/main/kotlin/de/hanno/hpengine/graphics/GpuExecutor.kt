package de.hanno.hpengine.graphics

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface GpuExecutor {
    suspend fun <T> execute(block: () -> T): T
    operator fun <T> invoke(block: () -> T): T

    fun launch(block: () -> Unit) {
        GlobalScope.launch {
            execute(block)
        }
    }

}
