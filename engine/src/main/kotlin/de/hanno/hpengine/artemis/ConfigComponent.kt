package de.hanno.hpengine.artemis

import com.artemis.Component
import com.artemis.annotations.Wire
import de.hanno.hpengine.config.Config
import net.mostlyoriginal.api.Singleton

@Singleton
class ConfigComponent: Component() {
    @Wire
    lateinit var config: Config
}