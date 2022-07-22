package de.hanno.hpengine.graphics

interface GpuFeature {
    val defineString: String
}

abstract class AbstractGpuFeature(override val defineString: String): GpuFeature {
    override fun toString() = this::class.java.simpleName
}

object BindlessTextures: AbstractGpuFeature(BindlessTextures::class.java.simpleName)
object DrawParameters: AbstractGpuFeature(DrawParameters::class.java.simpleName)
object NvShader5: AbstractGpuFeature("SHADER5")
object ArbShader5: AbstractGpuFeature("ARBSHADER5")
object ArbShaderInt64: AbstractGpuFeature("ARBSHADERINT64")