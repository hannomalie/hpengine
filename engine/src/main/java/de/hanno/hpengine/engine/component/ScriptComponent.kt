package de.hanno.hpengine.engine.component

import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.Reloadable

interface ScriptComponent: Component, Reloadable {
    val codeSource: CodeSource

    operator fun get(key: Any): Any
    fun put(key: Any, value: Any): Any
}
