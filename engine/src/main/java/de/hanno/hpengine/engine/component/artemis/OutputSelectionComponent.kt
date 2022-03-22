package de.hanno.hpengine.engine.component.artemis

import com.artemis.BaseSystem
import com.artemis.Component
import de.hanno.hpengine.engine.model.texture.Texture2D

class FinalOutputComponent: Component() {
    var texture2D: Texture2D? = null
}

class FinalOutputSystem: BaseSystem() {
    lateinit var finalOutputComponent: FinalOutputComponent

    override fun processSystem() {

    }
}