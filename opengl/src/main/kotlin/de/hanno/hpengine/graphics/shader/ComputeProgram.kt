package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.ressources.FileMonitor

class ComputeProgram(
    val computeShader: ComputeShader,
    private val graphicsApi: GraphicsApi,
    private val fileMonitor: FileMonitor,
) : AbstractProgram<Uniforms>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    uniforms = Uniforms.Empty,
    graphicsApi,
    fileMonitor,
), IComputeProgram<Uniforms> {
    override fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        graphicsApi.dispatchCompute(num_groups_x, num_groups_y, num_groups_z)
    }
}
