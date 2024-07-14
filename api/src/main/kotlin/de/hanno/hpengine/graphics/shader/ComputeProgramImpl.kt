package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.graphics.GraphicsApi

class ComputeProgramImpl<T: Uniforms>(
    val computeShader: ComputeShader,
    private val graphicsApi: GraphicsApi,
    override val uniforms: T,
) : AbstractProgram<T>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    graphicsApi,
), ComputeProgram<T> {
    override fun dispatchCompute(numGroupsX: ElementCount, numGroupsY: ElementCount, numGroupsZ: ElementCount) {
        graphicsApi.dispatchCompute(numGroupsX.value.toInt(), numGroupsY.value.toInt(), numGroupsZ.value.toInt())
    }
}
