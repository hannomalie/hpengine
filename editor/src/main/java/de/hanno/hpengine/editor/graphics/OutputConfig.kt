package de.hanno.hpengine.editor.graphics

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import struktgen.api.Strukt
import java.nio.ByteBuffer

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
