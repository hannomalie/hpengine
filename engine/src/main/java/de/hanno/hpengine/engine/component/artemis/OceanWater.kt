package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import org.joml.Vector2f
import org.joml.Vector3f

class OceanWaterComponent: Component() {
    var amplitude: Float = 2f
    var windspeed: Float = recommendedIntensity
    var timeFactor: Float = 1f
    var direction: Vector2f = Vector2f(0.25f, 1.0f)
    var albedo: Vector3f = Vector3f(0f, 0.1f, 1f)
    var L: Int = 800

    companion object {
        val recommendedIntensity = 26f
    }
}

// TODO: Implement oceanwater material as component
//materialInfo.apply {
//    lodFactor = 100.0f
//    roughness = 0.0f
//    metallic = 1.0f
//    diffuse.set(0f, 0.1f, 1f)
//    ambient = 0.05f
//    parallaxBias = 0.0f
//    put(SimpleMaterial.MAP.DISPLACEMENT, oceanWaterRenderSystem.displacementMap)
//    put(SimpleMaterial.MAP.NORMAL, oceanWaterRenderSystem.normalMap)
//    put(SimpleMaterial.MAP.DIFFUSE, oceanWaterRenderSystem.albedoMap)
//    put(SimpleMaterial.MAP.ROUGHNESS, oceanWaterRenderSystem.roughnessMap)
//    put(SimpleMaterial.MAP.ENVIRONMENT, scene.get<TextureManager>().cubeMap)
//    put(SimpleMaterial.MAP.DIFFUSE, oceanWaterRenderSystem.displacementMap)
//}
//}