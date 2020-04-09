package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import org.joml.Vector3f
import java.util.ArrayList

class RenderBatch(
        var entityIndex: Int = -1,
        var meshIndex: Int = -1,
        var movedInCycle: Long = 0L,
        var isDrawLines: Boolean = false,
        var entityMinWorld: Vector3f = Vector3f(),
        var entityMaxWorld: Vector3f = Vector3f(),
        var meshMinWorld: Vector3f = Vector3f(),
        var meshMaxWorld: Vector3f = Vector3f(),
        var cameraWorldPosition: Vector3f = Vector3f(),
        var isVisibleForCamera: Boolean = false,
        var update: Update = Update.STATIC,
        val drawElementsIndirectCommand: DrawElementsIndirectCommand = DrawElementsIndirectCommand(),
        var centerWorld: Vector3f = Vector3f(),
        var animated : Boolean = false,
        var boundingSphereRadius: Float = 0.0f,
        var materialInfo: MaterialInfo = SimpleMaterialInfo("Dummy"),
        var entityBufferIndex: Int = 0) {

    val instanceCount: Int
        get() = drawElementsIndirectCommand.primCount

    val vertexCount: Int
        get() = drawElementsIndirectCommand.count / 3

    val isStatic: Boolean
        get() = !animated
}
class RenderBatches : ArrayList<RenderBatch>()
