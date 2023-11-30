package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.ressources.FileMonitor

class ComputeProgramImpl<T: Uniforms>(
    val computeShader: ComputeShader,
    private val graphicsApi: GraphicsApi,
    private val fileMonitor: FileMonitor,
    override val uniforms: T,
) : AbstractProgram<T>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    graphicsApi,
), ComputeProgram<T> {
    override fun dispatchCompute(numGroupsX: Int, numGroupsY: Int, numGroupsZ: Int) {
        graphicsApi.dispatchCompute(numGroupsX, numGroupsY, numGroupsZ)
    }
}
