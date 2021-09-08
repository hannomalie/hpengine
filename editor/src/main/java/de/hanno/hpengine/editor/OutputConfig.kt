package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D

sealed class OutputConfig {
    object Default : OutputConfig() {
        override fun toString(): String = "Default"
    }

    class Texture2D(
        val name: String,
        val texture: de.hanno.hpengine.engine.model.texture.Texture2D,
        val factorForDebugRendering: Float
    ) : OutputConfig() {
        override fun toString() = name
    }

    class TextureCubeMap(val name: String, val texture: de.hanno.hpengine.engine.model.texture.CubeMap) :
        OutputConfig() {
        override fun toString() = name
    }

    data class RenderTargetCubeMapArray(val renderTarget: CubeMapArrayRenderTarget, val cubeMapIndex: Int) :
        OutputConfig() {
        init {
            val cubeMapArraySize = renderTarget.arraySize
            require(cubeMapIndex < cubeMapArraySize) { "CubeMap index $cubeMapIndex is ot of bounds. Should be smaller than $cubeMapArraySize" }
        }

        override fun toString() = renderTarget.name + cubeMapIndex.toString()
    }
}