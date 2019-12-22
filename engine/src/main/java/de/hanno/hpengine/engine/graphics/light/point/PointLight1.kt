package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.model.IndexBuffer
import org.joml.Matrix4f
import org.joml.Vector4f
import java.io.Serializable
import java.nio.ByteBuffer

class PointLight(override val entity: Entity,
                 val color: Vector4f,
                 var radius: Float = 10f) : Component, Serializable, Bufferable {

    fun draw(program: Program?) {
        throw IllegalStateException("Currently not implemented!")
        //		if(!isInitialized()) { return; }
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().draw();
//		});
    }

    fun drawAgain(indexBuffer: IndexBuffer?, program: Program?) {
        throw IllegalStateException("Currently not implemented!")
        //		if(!isInitialized()) { return; }
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().drawAgain(indexBuffer);
//		});
    }

    private val tempOrientationMatrix = Matrix4f()
    //	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
//		Matrix4f temp = new Matrix4f();
//		Matrix4f.translate(getPosition(), temp, temp);
//		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
//		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
//		return temp;
//	}

    fun isInFrustum(camera: Camera): Boolean {
        val position = entity.position
        return camera.frustum.sphereInFrustum(position.x, position.y, position.z, radius)
    }

    override fun putToBuffer(buffer: ByteBuffer) {
        val worldPosition = entity.position
        buffer.putDouble(worldPosition.x.toDouble())
        buffer.putDouble(worldPosition.y.toDouble())
        buffer.putDouble(worldPosition.z.toDouble())
        buffer.putDouble(radius.toDouble())
        val color = color
        buffer.putDouble(color.x.toDouble())
        buffer.putDouble(color.y.toDouble())
        buffer.putDouble(color.z.toDouble())
        buffer.putDouble(-1.0)
    }

    override fun getBytesPerObject(): Int {
        return java.lang.Double.BYTES * 8
    }

    companion object {
        var COMPONENT_KEY = PointLight::class.java.simpleName
        private const val serialVersionUID = 1L
        var DEFAULT_RANGE = 1f
    }
}