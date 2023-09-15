package de.hanno.hpengine

import de.hanno.hpengine.config.*
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.fps.FPSCounterSystem
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
    @Single
    fun fpsCounterSystem() = FPSCounterSystem()
}
val apiModule = ApiModule().module
