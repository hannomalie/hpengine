package de.hanno.hpengine.engine.graphics

interface GpuFeature

abstract class AbstractGpuFeature: GpuFeature {
    override fun toString() = this::class.java.simpleName
}

object BindlessTextures: AbstractGpuFeature()
object DrawParameters: AbstractGpuFeature()
object Shader5: AbstractGpuFeature()