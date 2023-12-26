package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.graphics.shader.ProgramImpl
import de.hanno.hpengine.model.Update
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import org.joml.Vector3f

class RenderBatch(
    var entityIndex: Int = -1,
    var entityName: String = "Dummy",
    var meshIndex: Int = -1,
    var movedInCycle: Long = 0L,
    var isDrawLines: Boolean = false,
    var entityMinWorld: Vector3f = Vector3f(),
    var entityMaxWorld: Vector3f = Vector3f(),
    var meshMinWorld: Vector3f = Vector3f(),
    var meshMaxWorld: Vector3f = Vector3f(),
    var cameraWorldPosition: Vector3f = Vector3f(),
    var isVisibleForCamera: Boolean = false,
    var isVisible: Boolean = false,
    var update: Update = Update.STATIC,
    val drawElementsIndirectCommand: DrawElementsIndirectCommand = DrawElementsIndirectCommand(),
    val centerWorld: Vector3f = Vector3f(),
    var animated : Boolean = false,
    var boundingSphereRadius: Float = 0.0f,
    var material: Material = Material("default"),
    var program: ProgramImpl<*>? = null,
    var entityBufferIndex: Int = 0,
    var contributesToGi: Boolean = true
) {

    val isShadowCasting: Boolean get() = material.isShadowCasting

    val instanceCount: Int get() = drawElementsIndirectCommand.instanceCount

    val vertexCount: Int get() = drawElementsIndirectCommand.count / 3

    val isStatic: Boolean get() = !animated

    val hasOwnProgram get() = program != null
}
class RenderBatches : ArrayList<RenderBatch>()
