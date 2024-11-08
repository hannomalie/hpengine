package  de.hanno.hpengine.graphics.light.directional

import DirectionalLightStateImpl.Companion.type
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.state.Box
import de.hanno.hpengine.graphics.state.RenderStateContext
import org.koin.core.annotation.Single
import struktgen.api.forIndex

@Single
class DirectionalLightStateHolder(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
) {
    val camera = renderStateContext.renderState.registerState { Camera(Transform()) }
    val lightState = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(DirectionalLightState.type.sizeInBytes)).typed(DirectionalLightState.type).apply {
            forIndex(0) {
                it.shadowMapId = -1
                it.shadowMapHandle = -1L
            }

        }
    }
    val directionalLightHasMovedInCycle = renderStateContext.renderState.registerState { Box(0L) }
    val entityId = renderStateContext.renderState.registerState { Box(-1) }
}