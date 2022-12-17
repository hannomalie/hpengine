package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.shader.define.Defines

class Program<T: Uniforms>(
    val vertexShader: VertexShader,
    val fragmentShader: FragmentShader?,
    val geometryShader: GeometryShader?,
    val tesselationControlShader: TesselationControlShader?,
    val tesselationEvaluationShader: TesselationEvaluationShader?,
    uniforms: T,
    defines: Defines = Defines(),
    gpuContext: GpuContext,
): AbstractProgram<T>(
    shaders = listOfNotNull(
        vertexShader,
        fragmentShader,
        geometryShader,
        tesselationControlShader,
        tesselationEvaluationShader
    ),
    defines = defines,
    uniforms = uniforms,
    gpuContext = gpuContext,
)