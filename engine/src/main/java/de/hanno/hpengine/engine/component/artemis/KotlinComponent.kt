package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import de.hanno.hpengine.util.ressources.CodeSource

class KotlinComponent: Component() {
    lateinit var codeSource: CodeSource
}