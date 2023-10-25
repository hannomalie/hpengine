package de.hanno.hpengine.camera

import com.artemis.BaseSystem
import com.artemis.hackedOutComponents
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.system.Extractor
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, Extractor::class])
class CameraComponentSystem(
    private val config: Config,
    private val cameraComponentsStateHolder: CameraComponentsStateHolder,
): BaseSystem(), Extractor {

    override fun extract(currentWriteState: RenderState) {
        if (config.debug.isDrawCameras) {
            currentWriteState[cameraComponentsStateHolder.frustumLines].apply {
                clear()
                val components = world.getMapper(CameraComponent::class.java).hackedOutComponents
                components.indices.forEach { i ->
                    // TODO: cache frustum somehow for camera components
//                    val corners = components[i].frustumCorners
//
//                    addLine(corners[0], corners[1])
//                    addLine(corners[1], corners[2])
//                    addLine(corners[2], corners[3])
//                    addLine(corners[3], corners[0])
//
//                    addLine(corners[4], corners[5])
//                    addLine(corners[5], corners[6])
//                    addLine(corners[6], corners[7])
//                    addLine(corners[7], corners[4])
//
//                    addLine(corners[0], corners[6])
//                    addLine(corners[1], corners[7])
//                    addLine(corners[2], corners[4])
//                    addLine(corners[3], corners[5])
                }
            }
        }
    }

    override fun processSystem() { }
}