package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuCommandSync
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.material.MaterialStruct
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.copyFrom
import org.joml.Vector3f

class RenderState(private val gpuContext: GpuContext<*>) {
    val customState = CustomStates()

    val latestDrawResult = DrawResult(FirstPassResult(), SecondPassResult())

    var time = System.currentTimeMillis()

    val directionalLightState = PersistentMappedStructBuffer(1, gpuContext, { DirectionalLightState() })

    val lightState = LightState(gpuContext)

    val entitiesState: EntitiesState = EntitiesState(gpuContext)

    val environmentProbesState = EnvironmentProbeState(gpuContext)

    var skyBoxMaterialIndex = -1

    var camera = Camera(Entity("RenderStateCameraEntity"), 1280f/720f)
    var pointLightMovedInCycle: Long = 0
    var directionalLightHasMovedInCycle: Long = 0
    var sceneInitiallyDrawn: Boolean = false // TODO: Remove this completely
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

    val entitiesBuffer: PersistentMappedStructBuffer<EntityStruct>
        get() = entitiesState.entitiesBuffer

    val materialBuffer: PersistentMappedStructBuffer<MaterialStruct>
        get() = entitiesState.materialBuffer

    var deltaSeconds: Float = 0.1f

    constructor(source: RenderState) : this(source.gpuContext) {
        entitiesState.vertexIndexBufferStatic = source.entitiesState.vertexIndexBufferStatic
        entitiesState.vertexIndexBufferAnimated = source.entitiesState.vertexIndexBufferAnimated
        camera.init(source.camera)
        directionalLightState[0].copyFrom(source.directionalLightState[0])
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
        latestDrawResult.set(latestDrawResult)
        entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic)
        entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated)
    }
    val gpuHasFinishedUsingIt
        get() = gpuCommandSync.isSignaled
    fun addStatic(batch: RenderBatch) {
        entitiesState.renderBatchesStatic.add(batch)
    }

    fun addAnimated(batch: RenderBatch) {
        entitiesState.renderBatchesAnimated.add(batch)
    }

    fun add(state: Any) = customState.add(state)

    operator fun <T> get(stateRef: StateRef<T>) = customState[stateRef]

}
interface RenderSystem: Updatable {
    @JvmDefault fun render(result: DrawResult, renderState: RenderState) { }
    @JvmDefault fun renderEditor(result: DrawResult, renderState: RenderState) { }
    @JvmDefault fun afterFrameFinished() { }
    @JvmDefault fun extract(scene: Scene, renderState: RenderState) { }
    @JvmDefault fun beforeSetScene(nextScene: Scene) { }
}

class CustomStates {
    private val states = mutableListOf<Any>()
    fun add(state: Any) {
        states.add(state)
    }

    operator fun <T> get(ref: StateRef<T>) = states[ref.index] as T

    fun clear() = states.clear()
}

class StateRef<out T>(val index: Int)
