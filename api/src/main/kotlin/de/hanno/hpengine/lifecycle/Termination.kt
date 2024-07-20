package de.hanno.hpengine.lifecycle

import com.artemis.BaseSystem
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean

@Single
class Termination: BaseSystem() {
    val terminationRequested = AtomicBoolean(false)
    val terminationAllowed = AtomicBoolean(false)

    override fun processSystem() {
    }
}