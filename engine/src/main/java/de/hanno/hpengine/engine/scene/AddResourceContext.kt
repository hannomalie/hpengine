package de.hanno.hpengine.engine.scene

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed class UpdateLock(val context: AddResourceContext)

private class PrivateUpdateLock(context: AddResourceContext): UpdateLock(context)

class AddResourceContext {
    private val updateLock: UpdateLock = PrivateUpdateLock(this)
    private val lock = ReentrantLock()

    fun launch(block: () -> Unit) {
        GlobalScope.launch {
            lock.withLock {
                block()
            }
        }
    }

    fun <T> locked(block: () -> T): T = lock.withLock {
        block()
    }
}