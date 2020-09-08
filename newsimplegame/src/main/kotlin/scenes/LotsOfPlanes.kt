package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector3f
import kotlin.random.Random

val Engine.lotsOfPlanesScene
    get() = scene("LotsOfCubes") {
        entities {
            entity("Plane") {
                modelComponent(
                    name = "Plane",
                    file = "assets/models/planeRotated.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    material.materialInfo.put(SimpleMaterial.MAP.DIFFUSE, engineContext.textureManager.getTexture("assets/textures/grass.png", true))
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                }
                addComponent(ClustersComponent(this@entity).apply {
                    val boundsXZ = 100f
                    val instancesCount = 10000
                    val instancesCountHalf = instancesCount /2
                    val distancePerInstance = boundsXZ / instancesCount.toFloat()
                    addInstances((-instancesCountHalf .. instancesCountHalf).map {
                        Instance(this@entity, Transform().apply {
                            val generator = { it * distancePerInstance * Random.nextFloat() }
                            transformation.scale(Vector3f(1f).add(Vector3f(Random.nextFloat() *0.1f, Random.nextFloat(), Random.nextFloat() *0.1f)))
                            transformation.rotateLocal(Math.toRadians(180.0*Random.nextDouble()).toFloat(), 0f, 1f, 0f)
                            transformation.translate(Vector3f(generator(), 0f, generator()))
                        })
                    })
                })
            }
        }
    }