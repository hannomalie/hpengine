package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.scene.VertexIndexBuffer

class DirectDrawDescription<T: Uniforms>(
    val renderState: RenderState,
    val renderBatches: List<RenderBatch>,
    val program: Program<T>,
    val vertexIndexBuffer: VertexIndexBuffer<*>,
    val beforeDraw: (RenderState, Program<T>, Camera) -> Unit,
    val mode: RenderingMode,
    val drawCam: Camera,
    val cullCam: Camera = drawCam,
    val ignoreCustomPrograms: Boolean,
)
