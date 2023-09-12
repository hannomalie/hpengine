package de.hanno.hpengine.graphics.renderer.deferred

import de.hanno.hpengine.graphics.output.DebugOutput
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.renderer.IdTexture
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module

@Module
@ComponentScan
class DeferredRendererModule {
    @Single
    fun idTexture(
        deferredRenderingBuffer: DeferredRenderingBuffer
    ) = IdTexture(deferredRenderingBuffer.depthAndIndicesMap)
    @Single
    fun finalOutput(
        deferredRenderingBuffer: DeferredRenderingBuffer
    ) = FinalOutput(deferredRenderingBuffer.finalMap)
    @Single
    fun debugOutput(): DebugOutput = DebugOutput(null, 0)
}

val deferredRendererModule = DeferredRendererModule().module