package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.ressources.FileMonitor

class ProgramImpl<T: Uniforms>(
    val vertexShader: VertexShader,
    val fragmentShader: FragmentShader?,
    val geometryShader: GeometryShader?,
    val tesselationControlShader: TesselationControlShader?,
    val tesselationEvaluationShader: TesselationEvaluationShader?,
    uniforms: T,
    defines: Defines = Defines(),
    graphicsApi: GraphicsApi,
    fileMonitor: FileMonitor,
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
    graphicsApi = graphicsApi,
)