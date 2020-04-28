package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.VertexIndexBuffer

class DrawDescription(
    val renderState: RenderState,
    val program: Program,
    val commandOrganization: CommandOrganization,
    val vertexIndexBuffer: VertexIndexBuffer,
    val drawCam: Camera,
    val cullCam: Camera = drawCam
)