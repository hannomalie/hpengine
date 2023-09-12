package de.hanno.hpengine.graphics.light.directional

import DirectionalLightStateImpl.Companion.type
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.Transform
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.state.DirectionalLightState
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW
import struktgen.api.forIndex
import struktgen.api.get


@All(DirectionalLightComponent::class, TransformComponent::class)
class DirectionalLightSystem(
    private val directionalLightStateHolder: DirectionalLightStateHolder,
) : BaseEntitySystem(), Extractor, WorldPopulator {
    @Wire
    lateinit var input: Input
    lateinit var cycleSystem: CycleSystem
    lateinit var directionalLightComponentMapper: ComponentMapper<DirectionalLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var cameraComponentMapper: ComponentMapper<CameraComponent>

    private val lightState = directionalLightStateHolder.lightState

    override fun processSystem() {
        val moveAmount = 100 * world.delta
        val degreesPerSecond = 45f
        val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * world.delta

        forEachEntity {
            val transform = transformComponentMapper[it].transform

            if (input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                transform.rotateAround(Vector3f(0f, 1f, 0f), rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                transform.rotateAround(Vector3f(0f, 1f, 0f), -rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
                transform.rotateAround(Vector3f(1f, 0f, 0f), rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
                transform.rotateAround(Vector3f(1f, 0f, 0f), -rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_8)) {
                transform.translate(Vector3f(0f, -moveAmount, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_2)) {
                transform.translate(Vector3f(0f, moveAmount, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_4)) {
                transform.translate(Vector3f(-moveAmount, 0f, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_6)) {
                transform.translate(Vector3f(moveAmount, 0f, 0f))
            }
        }
    }

    override fun extract(currentWriteState: RenderState) {
        currentWriteState.set(directionalLightStateHolder.directionalLightHasMovedInCycle, currentWriteState.cycle)

        if(!subscription.entities.isIndexWithinBounds(0)) return

        val light = directionalLightComponentMapper.get(subscription.entities[0])
        val transform = transformComponentMapper.get(subscription.entities[0]).transform
        val camera = cameraComponentMapper.get(subscription.entities[0])

        currentWriteState[lightState].typedBuffer.forIndex(0) { directionalLightState ->
            directionalLightState.color.set(light.color)
            directionalLightState.direction.set(transform.viewDirection)
            directionalLightState.scatterFactor = light.scatterFactor
            val viewMatrix = Matrix4f(transform).invert()
            directionalLightState.viewMatrix.set(viewMatrix)
            directionalLightState.projectionMatrix.set(camera.projectionMatrix)
            directionalLightState.viewProjectionMatrix.set(Matrix4f(camera.projectionMatrix).mul(viewMatrix))
        }
    }

    override fun World.populate() {
        addDirectionalLight()
    }
}


@Single
class DirectionalLightStateHolder(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
) {
    val lightState = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(DirectionalLightState.type.sizeInBytes).typed(DirectionalLightState.type).apply {
            forIndex(0) {
                it.shadowMapId = -1
                it.shadowMapHandle = -1L
            }

        }
    }
    val directionalLightHasMovedInCycle = renderStateContext.renderState.registerState { 0L }
}

fun World.addDirectionalLight() {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "DirectionalLight"
        }

        create(DirectionalLightComponent::class.java).apply { }
        create(TransformComponent::class.java).apply {
            transform = Transform().apply {
                translate(Vector3f(12f, 300f, 2f))
                rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), 1f, 0f, 0f)), 0f, 0f, 0f)
            }
        }
        create(
            CameraComponent()::class.java
        ).apply {
            width = 1500f
            height = 1500f
            far = (-5000).toFloat()
            perspective = false
        }
    }
}
