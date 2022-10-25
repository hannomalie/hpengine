package de.hanno.hpengine.graphics.shader.api

import de.hanno.hpengine.ressources.CodeSource


interface Shader {
    fun unload()
    fun reload()

    val source: CodeSource
    val id: Int
}