package de.hanno.hpengine.graphics.renderer.deferred

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.gi.GiVolumeStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.output.DebugOutput
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.probe.EvaluateProbeRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.graphics.renderer.picking.OnClickListener
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.vct.VoxelConeTracingExtension
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.input.PixelPerfectPickingExtension
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.material.MaterialManager
import de.hanno.hpengine.skybox.SkyBoxStateHolder
import de.hanno.hpengine.skybox.SkyboxRenderExtension
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.koin.ksp.generated.module

@Module
@ComponentScan
class DeferredRendererModule {
    @Single
    fun idTexture(
        deferredRenderingBuffer: DeferredRenderingBuffer,
    ) = IdTexture(deferredRenderingBuffer.depthAndIndicesMap)

    @Single
    fun finalOutput(
        deferredRenderingBuffer: DeferredRenderingBuffer,
    ) = FinalOutput(deferredRenderingBuffer.finalMap)

    @Single
    fun debugOutput(): DebugOutput = DebugOutput(null, 0)

//     TODO: Reenable, currently shader compilation errors
//    @Single(binds = [DeferredRenderExtension::class])
//    fun evaluateProbeRenderExtension(
//        graphicsApi: GraphicsApi,
//        programManager: ProgramManager,
//        config: Config,
//        deferredRenderingBuffer: DeferredRenderingBuffer,
//        directionalLightStateHolder: DirectionalLightStateHolder,
//        entitiesStateHolder: EntitiesStateHolder,
//        primaryCameraStateHolder: PrimaryCameraStateHolder,
//    ) = EvaluateProbeRenderExtension(
//        graphicsApi,
//        programManager,
//        config,
//        deferredRenderingBuffer,
//        directionalLightStateHolder,
//        entitiesStateHolder,
//        primaryCameraStateHolder,
//    )

    @Single(binds = [VoxelConeTracingExtension::class, DeferredRenderExtension::class])
    fun voxelConeTracingExtension(
        graphicsApi: GraphicsApi,
        renderStateContext: RenderStateContext,
        config: Config,
        programManager: ProgramManager,
        pointLightExtension: BvHPointLightSecondPassExtension,
        deferredRenderingBuffer: DeferredRenderingBuffer,
        directionalLightStateHolder: DirectionalLightStateHolder,
        pointLightStateHolder: PointLightStateHolder,
        entitiesStateHolder: EntitiesStateHolder,
        skyBoxStateHolder: SkyBoxStateHolder,
        primaryCameraStateHolder: PrimaryCameraStateHolder,
        giVolumeStateHolder: GiVolumeStateHolder,
    ) = VoxelConeTracingExtension(
        graphicsApi,
        renderStateContext,
        config,
        programManager,
        pointLightExtension,
        deferredRenderingBuffer,
        directionalLightStateHolder,
        pointLightStateHolder,
        entitiesStateHolder,
        skyBoxStateHolder,
        primaryCameraStateHolder,
        giVolumeStateHolder,
    )

    @Single(binds = [PixelPerfectPickingExtension::class, DeferredRenderExtension::class])
    fun pixelPerfectPickingExtension(
        graphicsApi: GraphicsApi,
        config: Config,
        input: Input,
        listeners: List<OnClickListener>,
        window: Window,
    ) = PixelPerfectPickingExtension(
        graphicsApi,
        config,
        input,
        listeners,
        window,
    )

    @Single(binds = [SkyboxRenderExtension::class, DeferredRenderExtension::class])
    fun skyboxRenderExtension(
        graphicsApi: GraphicsApi,
        config: Config,
        deferredRenderingBuffer: DeferredRenderingBuffer,
        programManager: ProgramManager,
        textureManager: OpenGLTextureManager,
        materialManager: MaterialManager,
        entitiesStateHolder: EntitiesStateHolder,
        primaryCameraStateHolder: PrimaryCameraStateHolder,
        skyBoxStateHolder: SkyBoxStateHolder,
    ) = SkyboxRenderExtension(
        graphicsApi,
        config,
        deferredRenderingBuffer,
        programManager,
        textureManager,
        materialManager,
        entitiesStateHolder,
        primaryCameraStateHolder,
        skyBoxStateHolder,
    )
}

val deferredRendererModule = DeferredRendererModule().module