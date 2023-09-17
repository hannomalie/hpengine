package de.hanno.hpengine.lifecycle

import com.artemis.BaseSystem
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicLong

@Single
class UpdateCycle: BaseSystem() {
    val cycle = AtomicLong()
    override fun processSystem() { }
}