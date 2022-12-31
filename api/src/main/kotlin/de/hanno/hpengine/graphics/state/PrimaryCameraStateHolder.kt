package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.GraphicsApi

context(GraphicsApi, RenderStateContext)
class PrimaryCameraStateHolder {
    val camera = renderState.registerState {
        Camera(Transform(), 1280f / 720f)
    }
}