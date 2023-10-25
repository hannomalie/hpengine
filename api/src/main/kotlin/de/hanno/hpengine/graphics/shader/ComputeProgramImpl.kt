package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.ressources.FileMonitor

class ComputeProgramImpl(
    val computeShader: ComputeShader,
    private val graphicsApi: GraphicsApi,
    private val fileMonitor: FileMonitor,
    override val uniforms: Uniforms,
) : AbstractProgram<Uniforms>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    graphicsApi,
), ComputeProgram<Uniforms> {
    override fun dispatchCompute(numGroupsX: Int, numGroupsY: Int, numGroupsZ: Int) {
        graphicsApi.dispatchCompute(numGroupsX, numGroupsY, numGroupsZ)
    }
}
