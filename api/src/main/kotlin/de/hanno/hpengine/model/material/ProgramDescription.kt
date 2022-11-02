package de.hanno.hpengine.model.material

import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.ressources.CodeSource

data class ProgramDescription(
    val fragmentShaderSource: CodeSource,
    val vertexShaderSource: CodeSource,
    val tesselationControlShaderSource : CodeSource? = null,
    val tesselationEvaluationShaderSource : CodeSource? = null,
    val geometryShaderSource: CodeSource? = null,
    val defines: Defines? = null,
)