package de.hanno.hpengine.artemis

import com.artemis.BaseSystem
import com.artemis.Component
import de.hanno.hpengine.model.texture.OpenGLTexture2D

class FinalOutputComponent: Component() {
    var texture2D: OpenGLTexture2D? = null
}

class FinalOutputSystem: BaseSystem() {
    lateinit var finalOutputComponent: FinalOutputComponent

    override fun processSystem() {

    }
}