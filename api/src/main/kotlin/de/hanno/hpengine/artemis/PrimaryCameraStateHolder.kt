package de.hanno.hpengine.artemis

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderStateContext

context(GraphicsApi, RenderStateContext)
class PrimaryCameraStateHolder {
    val camera = renderState.registerState {
        Camera(Transform(), 1280f / 720f)
    }
}