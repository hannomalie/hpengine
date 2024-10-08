package de.hanno.hpengine

import com.artemis.BaseSystem
import com.artemis.link.EntityLinkManager
import com.artemis.managers.TagManager
import de.hanno.hpengine.config.*
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.fps.FPSCounterSystem
import de.hanno.hpengine.graphics.output.DebugOutput
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module

@Module
@ComponentScan
class ApiModule {
    @Single
    fun qualityConfig() = QualityConfig()
    @Single
    fun debugConfig() = DebugConfig()
    @Single
    fun effectsConfig() = EffectsConfig()
    @Single
    fun performanceConfig() = PerformanceConfig()
    @Single
    fun profilingConfig() = ProfilingConfig()

    @Single
    fun depthBuffer(graphicsApi: GraphicsApi, config: Config) = graphicsApi.DepthBuffer(config.width, config.height)

    @Single
    fun fpsCounter(fpsCounterSystem: FPSCounterSystem) = fpsCounterSystem.fpsCounter
    @Single(binds = [RenderSystem::class, FPSCounterSystem::class])
    fun fpsCounterSystem() = FPSCounterSystem()

    @Single(binds = [TagManager::class, BaseSystem::class])
    fun tagManager() = TagManager()
    @Single(binds = [EntityLinkManager::class, BaseSystem::class])
    fun entityLinkManager() = EntityLinkManager()

    @Single
    fun debugOutput() = DebugOutput(null, 0)
}
val apiModule = ApiModule().module
