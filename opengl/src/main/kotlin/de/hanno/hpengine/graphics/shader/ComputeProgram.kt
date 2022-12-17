package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GpuContext

class ComputeProgram(
    val computeShader: ComputeShader,
    private val gpuContext: GpuContext,
) : AbstractProgram<Uniforms>(
    shaders = listOf(computeShader),
    defines = computeShader.defines,
    uniforms = Uniforms.Empty,
    gpuContext,
), IComputeProgram<Uniforms> {
    override fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        gpuContext.dispatchCompute(num_groups_x, num_groups_y, num_groups_z)
    }
}
