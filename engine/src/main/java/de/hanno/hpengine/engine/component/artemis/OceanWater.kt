package de.hanno.hpengine.engine.component.artemis

import com.artemis.Component
import org.joml.Vector2f
import org.joml.Vector3f
import java.lang.Math.pow
import kotlin.math.pow

class OceanWaterComponent: Component() {
    var amplitude: Float = 2f
    var windspeed: Float = recommendedIntensity
    var timeFactor: Float = 1f
    var direction: Vector2f = Vector2f(0.25f, 1.0f)
    var albedo: Vector3f = Vector3f(0f, 0.1f, 1f)
    var choppy: Boolean = false
    var scaleX = 1f
    var scaleY = 1f
    var scaleZ = 1f

    val L: Int
        get() = (windspeed.pow(2.0f) /9.81f).toInt()

    companion object {
        val recommendedIntensity = 26f
    }
}
class OceanSurfaceComponent: Component() {
    var mapsSet = false // this prevents oceanwaterextension from assigning maps every time extraction is done
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