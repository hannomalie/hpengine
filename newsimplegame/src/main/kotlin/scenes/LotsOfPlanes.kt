package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.enhanced
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import kotlin.random.Random

val Engine.lotsOfPlanesScene
    get() = scene("LotsOfPlanes") {
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
                    // https://digitalrune.github.io/DigitalRune-Documentation/html/fa431d48-b457-4c70-a590-d44b0840ab1e.htm
                    firstpassProgramFragmentSource.enhanced {
                        replace("//END","""
                            mat4 thresholdMatrix = mat4(
                                1.0f / 17.0f,  9.0f / 17.0f,  3.0f / 17.0f, 11.0f / 17.0f,
                                13.0f / 17.0f,  5.0f / 17.0f, 15.0f / 17.0f,  7.0f / 17.0f,
                                4.0f / 17.0f, 12.0f / 17.0f,  2.0f / 17.0f, 10.0f / 17.0f,
                                16.0f / 17.0f,  8.0f / 17.0f, 14.0f / 17.0f,  6.0f / 17.0f
                            );

                            vec3 positionView = (viewMatrix * position_world).xyz;

                            float threshold = thresholdMatrix[int(gl_FragCoord.x) % 4][int(gl_FragCoord.y) % 4];
                            float distanceBias = 20.0f;
                            float distanceToFadeOver = 30.0f;
                            float distanceAlpha = 1 - ((-positionView.z - distanceBias)/distanceToFadeOver);
                            if((distanceAlpha - threshold) < 0.0f)
                            {
                                discard;
                            }
                        """.trimIndent())
                    }, StaticFirstPassUniforms(engineContext.gpuContext)
                ) as Program<FirstPassUniforms>

                modelComponent(
                    name = "Plane",
                    file = "assets/blender_grass/Low/Low Grass.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    material.materialInfo.put(SimpleMaterial.MAP.HEIGHT, engineContext.textureManager.getTexture("assets/blender_grass/Low/Grass_height.png", false))
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                    material.materialInfo.program = simpleColorProgramStatic
                }

                val instancesPerCluster = 100
                val center = Vector3f()
                val size = Vector3f(100f, 10f, 100f)
                val startCorner = Vector3f(center).sub(Vector3f(size).mul(0.5f))
                val cellDimension = Vector3f(2f, 10f, 2f)
                val cellCount = Vector3f(size.x / cellDimension.x, size.y / cellDimension.y, size.z / cellDimension.z)
                val clusters = (0 until cellCount.x.toInt()).flatMap { x ->
                    (0 until cellCount.y.toInt()).flatMap { y ->
                        (0 until cellCount.z.toInt()).flatMap { z ->
                            val min = Vector3f(cellDimension).mul(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()))
                            val instances = (0 until instancesPerCluster).map {
                                val position = Vector3f(Random.nextFloat() * cellDimension.x, size.y * 0.5f, Random.nextFloat() * cellDimension.z).add(startCorner).add(min)
                                val transformation = Transform().apply {
                                    scale(Vector3f(10f).add(Vector3f(Random.nextFloat() * 0.1f, Random.nextFloat(), Random.nextFloat() * 0.1f)))
                                    rotateAround(Vector3f(0f, 1f, 0f), Math.toRadians(180.0 * Random.nextDouble()).toFloat(), position)
                                    translate(position)
                                }
                                val spatial1 = object : StaticTransformSpatial(transformation, this@entity.getComponent(ModelComponent::class.java)!!) {
                                    init {
                                        boundingVolume.recalculate(transformation)
                                    }

                                    override suspend fun update(scene: Scene, deltaSeconds: Float) {}
                                }
                                Instance(this@entity, transformation, spatial = spatial1)
                            }
                            val cluster1 = Cluster().apply {
                                addAll(instances)
                            }
                            listOf(cluster1)
                        }
                    }
                }
                addComponent(ClustersComponent(this@entity).apply { addClusters(clusters) })
            }
        }
    }