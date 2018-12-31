package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuCommandSync
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.point.PointLightShadowMapStrategy
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.joml.Vector3f
import java.nio.FloatBuffer

class RenderState(gpuContext: GpuContext) {
    private val gpuContext: GpuContext = gpuContext

    val customState = CustomStateHolder()

    val latestDrawResult = DrawResult(FirstPassResult(), SecondPassResult())

    val directionalLightState = DirectionalLightState()

    val lightState = LightState(gpuContext)

    val entitiesState: EntitiesState = EntitiesState(gpuContext)

    val environmentProbesState = EnvironmentProbeState(gpuContext)

    var skyBoxMaterialIndex = -1

    val commandOrganizationStatic: CommandOrganization = CommandOrganization(gpuContext)
    val commandOrganizationAnimated: CommandOrganization = CommandOrganization(gpuContext)

    var camera = Camera(Entity("RenderStateCameraEntity"))
    var pointLightMovedInCycle: Long = 0
    var directionalLightHasMovedInCycle: Long = 0
    var sceneInitiallyDrawn: Boolean = false
    var sceneMin = Vector3f()
    var sceneMax = Vector3f()

    var cycle: Long = 0
    var gpuCommandSync: GpuCommandSync = object : GpuCommandSync {}

    val renderBatchesStatic: List<RenderBatch>
        get() = entitiesState.renderBatchesStatic
    val renderBatchesAnimated: List<RenderBatch>
        get() = entitiesState.renderBatchesAnimated

    val vertexIndexBufferStatic: VertexIndexBuffer
        get() = entitiesState.vertexIndexBufferStatic
    val vertexIndexBufferAnimated: VertexIndexBuffer
        get() = entitiesState.vertexIndexBufferAnimated

    val entitiesBuffer: GPUBuffer
        get() = entitiesState.entitiesBuffer

    val materialBuffer: GPUBuffer
        get() = entitiesState.materialBuffer

    val directionalLightViewMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightViewMatrixAsBuffer

    val directionalLightProjectionMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightProjectionMatrixAsBuffer

    val directionalLightViewProjectionMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightViewProjectionMatrixAsBuffer

    val staticEntityHasMoved: Boolean
        get() = entitiesState.staticEntityMovedInCycle == cycle
    var deltaInS: Float = 0.1f

    /**
     * Copy constructor
     * @param source
     */
    constructor(source: RenderState) : this(source.gpuContext) {
        entitiesState.vertexIndexBufferStatic = source.entitiesState.vertexIndexBufferStatic
        entitiesState.vertexIndexBufferAnimated = source.entitiesState.vertexIndexBufferAnimated
        entitiesState.joints = source.entitiesState.joints
        camera.init(source.camera)
        directionalLightState.directionalLightViewMatrixAsBuffer = source.directionalLightState.directionalLightViewMatrixAsBuffer
        directionalLightState.directionalLightViewMatrixAsBuffer.rewind()
        directionalLightState.directionalLightProjectionMatrixAsBuffer = source.directionalLightState.directionalLightProjectionMatrixAsBuffer
        directionalLightState.directionalLightProjectionMatrixAsBuffer.rewind()
        directionalLightState.directionalLightViewProjectionMatrixAsBuffer = source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer
        directionalLightState.directionalLightViewProjectionMatrixAsBuffer.rewind()
        directionalLightState.directionalLightDirection.set(source.directionalLightState.directionalLightDirection)
        directionalLightState.directionalLightColor.set(source.directionalLightState.directionalLightColor)
        directionalLightState.directionalLightScatterFactor = source.directionalLightState.directionalLightScatterFactor
        lightState.pointLights = source.lightState.pointLights
        lightState.pointLightBuffer = source.lightState.pointLightBuffer
        lightState.areaLights = source.lightState.areaLights
        lightState.tubeLights = source.lightState.tubeLights
        lightState.pointLightShadowMapStrategy = source.lightState.pointLightShadowMapStrategy
        lightState.areaLightDepthMaps = source.lightState.areaLightDepthMaps
        entitiesState.entityMovedInCycle = source.entitiesState.entityMovedInCycle
        entitiesState.staticEntityMovedInCycle = source.entitiesState.staticEntityMovedInCycle
        entitiesState.entityAddedInCycle = source.entitiesState.entityAddedInCycle
        environmentProbesState.environmapsArray3Id = source.environmentProbesState.environmapsArray3Id
        environmentProbesState.environmapsArray0Id = source.environmentProbesState.environmapsArray0Id
        environmentProbesState.activeProbeCount = source.environmentProbesState.activeProbeCount
        environmentProbesState.environmentMapMin = source.environmentProbesState.environmentMapMin
        environmentProbesState.environmentMapMax = source.environmentProbesState.environmentMapMax
        environmentProbesState.environmentMapWeights = source.environmentProbesState.environmentMapWeights
        skyBoxMaterialIndex = source.skyBoxMaterialIndex
        directionalLightHasMovedInCycle = source.directionalLightHasMovedInCycle
        pointLightMovedInCycle = source.pointLightMovedInCycle
        sceneInitiallyDrawn = source.sceneInitiallyDrawn
        sceneMin = source.sceneMin
        sceneMax = source.sceneMax
        prepareExtraction()
        latestDrawResult.set(latestDrawResult)
        this.entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic)
        this.entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated)

        //        TODO: This could be problematic. Copies all buffer contents to the copy's buffers
        //        this.entitiesState.entitiesBuffer.putValues(source.entitiesState.entitiesBuffer.getValuesAsFloats());
        //        this.entitiesState.materialBuffer.putValues(source.entitiesState.materialBuffer.getValuesAsFloats());
        //        this.entitiesState.vertexBuffer.putValues(source.getVertexBuffer().getValuesAsFloats());
        //        this.entitiesState.indexBuffer.put(source.getIndexBuffer().getValues());
    }

    fun prepareExtraction() {
//        this.latestDrawResult.set(latestDrawResult)
        this.entitiesState.renderBatchesStatic.clear()
        this.entitiesState.renderBatchesAnimated.clear()
    }

    fun addStatic(batch: RenderBatch) {
        entitiesState.renderBatchesStatic.add(batch)
    }

    fun addAnimated(batch: RenderBatch) {
        entitiesState.renderBatchesAnimated.add(batch)
    }

    fun preventSwap(currentStaging: RenderState, currentRead: RenderState): Boolean {
        return currentStaging.cycle < currentRead.cycle
    }

    fun setVertexIndexBufferStatic(vertexIndexBuffer: VertexIndexBuffer) {
        this.entitiesState.vertexIndexBufferStatic = vertexIndexBuffer
    }

    fun setVertexIndexBufferAnimated(vertexIndexBufferAnimated: VertexIndexBuffer) {
        this.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated
    }

    fun add(state: CustomState) = customState.add(state)

    fun <T> getState(stateRef: StateRef<T>) = customState.get(stateRef.index) as T
    fun pointLightHasMoved() = pointLightMovedInCycle >= cycle
    fun entityHasMoved() = entitiesState.entityMovedInCycle >= cycle
    fun entityWasAdded() = entitiesState.entityAddedInCycle >= cycle
}

class CustomStateHolder {
    private val states = mutableListOf<CustomState>()
    fun add(state: CustomState) {
        states.add(state)
    }

    fun get(index: Int) = states[index]

    fun update(writeState: RenderState) = states.forEach { it.update(writeState) }

    fun clear() = states.clear()
}

interface CustomState {
    fun update(writeState: RenderState) {}
}

class StateRef<out T>(val index: Int)

interface RenderSystem {
    fun render(result: DrawResult, state: RenderState)
}