package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.enhanced
import org.joml.Vector3f
import kotlin.random.Random

val Engine.lotsOfPlanesScene
    get() = scene("LotsOfCubes") {
        entities {
            entity("Ground") {
                modelComponent(
                    name = "Ground",
                    file = "assets/models/plane.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                }
                transform.scale(500f)
            }

            entity("Grass") {
                val simpleColorProgramStatic = programManager.getProgram(
                    firstpassProgramVertexSource.enhanced {
                        replace(
                            "//AFTER_POSITION",
                            """
                            vec3 strength = vec3(0.01f, 0.005f, 0.01f);
                            float randomPerInstance = rand(vec2(gl_InstanceID*0.01f, gl_InstanceID*0.1f));
                            vec4 positionModelOriginal = positionModel;
		                    float pivotOriginal = (texture(heightMap, texCoord).rgb).r;
                            float pivot = pivotOriginal;
                            float foo = randomPerInstance*sin(0.001f*int(time%10000));
                            float angleXLarge = pivot*radians(15*foo); 
                            float angleZLarge = pivot*radians(15*foo); 
                            mat4 rotLargeX = mat4(
                                1,0,0,0,
                                0,cos(angleXLarge),-sin(angleXLarge),0,
                                0,sin(angleXLarge),cos(angleXLarge),0,
                                0,0,0,1
                            );
                            mat4 rotLargeZ = mat4(
                                cos(angleZLarge),-sin(angleZLarge),0,0,
                                sin(angleZLarge),cos(angleZLarge),0,0,
                                0,0,1,0,
                                0,0,0,1
                            );
                            positionModel *= rotLargeX;
                            positionModel *= rotLargeZ;
                            
                            if(pivotOriginal < 0.69f || positionModelOriginal.y < 0.1f) {
                                pivot = 0.0f;
                            }
                            foo = randomPerInstance*sin(0.01f*int((time+1234+int(randomPerInstance*1000.0f))%2000));
                            float angleXSmall = pivot*radians(2.0f*foo); 
                            float angleZSmall = pivot*radians(3.0f*foo); 
                            float angleYSmall = pivot*radians(1.0f*foo); 
                            mat4 rotSmallX = mat4(
                                1,0,0,0,
                                0,cos(angleXSmall),-sin(angleXSmall),0,
                                0,sin(angleXSmall),cos(angleXSmall),0,
                                0,0,0,1
                            );
                            mat4 rotSmallZ = mat4(
                                cos(angleZSmall),-sin(angleZSmall),0,0,
                                sin(angleZSmall),cos(angleZSmall),0,0,
                                0,0,1,0,
                                0,0,0,1
                            );
                            mat4 rotSmallY = mat4(
                                cos(angleYSmall),0,sin(angleYSmall),0,
                                0,1,0,0,
                                -sin(angleYSmall),0,cos(angleYSmall),0,
                                0,0,0,1
                            );
                            positionModel *= rotSmallX;
                            positionModel *= rotSmallZ;
                            positionModel *= rotSmallY;
                            
	                        position_world = modelMatrix * positionModel;
                            """.trimIndent()
                        )
                    },
                    firstpassProgramFragmentSource
                )

                modelComponent(
                    name = "Plane",
                    file = "assets/blender_grass/Low/Low Grass.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir,
                    program = simpleColorProgramStatic
                ).apply {
                    material.materialInfo.put(SimpleMaterial.MAP.HEIGHT, engineContext.textureManager.getTexture("assets/blender_grass/Low/Grass_height.png", false))
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                }
                val clusterWidth = 4
                val clusterCountPerDimension = 4
                val clusterCountPerDimensionHalf = clusterCountPerDimension / 2
                val instancesPerCluster = 800
                transform.scale(10f)

                addComponent(ClustersComponent(this@entity).apply {

                    val clusterCenters = (-clusterCountPerDimensionHalf until clusterCountPerDimensionHalf).asSequence().flatMap { x ->
                        (-clusterCountPerDimensionHalf until clusterCountPerDimensionHalf).asSequence().map { z ->
                            Vector3f(x * clusterWidth.toFloat(), 0f, z * clusterWidth.toFloat())
                        }
                    }
                    val clusters = clusterCenters.map { clusterCenter ->
                        Cluster().also {
                            val instances = (0 until instancesPerCluster).map {
                                val instancePosition = Vector3f(Random.nextFloat() - 0.5f, 0f, Random.nextFloat() - 0.5f).mul(clusterWidth.toFloat()).add(clusterCenter)
                                val transformation = Transform().apply {
                                    scale(Vector3f(10f).add(Vector3f(Random.nextFloat() * 0.1f, Random.nextFloat(), Random.nextFloat() * 0.1f)))
                                    rotateLocal(Math.toRadians(180.0 * Random.nextDouble()).toFloat(), 0f, 1f, 0f)
                                    translate(instancePosition)
                                }
                                Instance(this@entity, transformation, spatial = StaticTransformSpatial(transformation, this@entity.getComponent(ModelComponent::class.java)!!))
                            }
                            it.addAll(instances)
                        }
                    }

                    addClusters(clusters.toList())
                })
            }
        }
    }