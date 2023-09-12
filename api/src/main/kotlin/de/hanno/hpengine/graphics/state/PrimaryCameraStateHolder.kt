package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.GraphicsApi
import org.koin.core.annotation.Single

@Single
class PrimaryCameraStateHolder(renderStateContext: RenderStateContext) {
    val camera = renderStateContext.renderState.registerState {
        Camera(Transform(), 1280f / 720f)
    }
}