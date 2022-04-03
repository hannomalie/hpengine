package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import com.artemis.annotations.Wire
import de.hanno.hpengine.engine.config.ConfigImpl
import net.mostlyoriginal.api.Singleton

@Singleton
class ConfigComponent: Component() {
    @Wire
    lateinit var config: ConfigImpl
}