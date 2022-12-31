package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.ressources.FileMonitor

class ComputeProgram(
    val computeShader: ComputeShader,
    private val gpuContext: GpuContext,
    private val fileMonitor: FileMonitor,
) : AbstractProgram<Uniforms>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    uniforms = Uniforms.Empty,
    gpuContext,
    fileMonitor,
), IComputeProgram<Uniforms> {
    override fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        gpuContext.dispatchCompute(num_groups_x, num_groups_y, num_groups_z)
    }
}
