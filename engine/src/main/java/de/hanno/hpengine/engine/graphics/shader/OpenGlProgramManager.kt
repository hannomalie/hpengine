package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.ReloadableCodeSource
import de.hanno.hpengine.util.ressources.StringBasedCodeSource
import de.hanno.hpengine.util.ressources.WrappedCodeSource
import de.hanno.hpengine.util.ressources.hasChanged
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class UniformDelegate<T>(var _value: T) : ReadWriteProperty<Uniforms, T> {
    lateinit var name: String
        internal set
    override fun getValue(thisRef: Uniforms, property: KProperty<*>): T = _value
    override fun setValue(thisRef: Uniforms, property: KProperty<*>, value: T) {
        _value = value
    }

}

class IntType(initial: Int = 0): UniformDelegate<Int>(initial)
class FloatType(initial: Float = 0f): UniformDelegate<Float>(initial)
class BooleanType(initial: Boolean): UniformDelegate<Boolean>(initial)
class Mat4(initial: FloatBuffer = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }) : UniformDelegate<FloatBuffer>(initial)
class Vec3(initial: Vector3f) : UniformDelegate<Vector3f>(initial)
class SSBO<T: Struct>(val dataType: String, val bindingIndex: Int, initial: PersistentMappedStructBuffer<T>) : UniformDelegate<PersistentMappedStructBuffer<T>>(initial)

open class Uniforms {
    val registeredUniforms = mutableListOf<UniformDelegate<*>>()

    operator fun <T> UniformDelegate<T>.provideDelegate(thisRef: Uniforms, prop: KProperty<*>): ReadWriteProperty<Uniforms, T> {
        return this.apply {
            this.name = prop.name
            thisRef.registeredUniforms.add(this)
        }
    }
    companion object {
        val Empty = Uniforms()
    }
}

class OpenGlProgramManager(override val gpuContext: OpenGLContext,
                           private val eventBus: EventBus,
                           val config: Config) : ProgramManager<OpenGl> {

    override fun List<UniformDelegate<*>>.toUniformDeclaration() = joinToString("\n") {
        when (it) {
            is Mat4 -> "uniform mat4 ${it.name};"
            is Vec3 -> "uniform vec3 ${it.name};"
            is SSBO<*> -> {
                """layout(std430, binding=${it.bindingIndex}) buffer _${it.name} {
                      ${it.dataType} ${it.name}[];
                   };""".trimIndent()
            }
            is IntType -> "uniform int ${it.name};"
            is BooleanType -> "uniform bool ${it.name};"
            is FloatType -> "uniform float ${it.name};"
        }
    }
    override val Uniforms.shaderDeclarations
        get() = registeredUniforms.toUniformDeclaration()

    var programsCache: MutableList<AbstractProgram<*>> = CopyOnWriteArrayList()

    override val linesProgram = run {
        val uniforms = LinesProgramUniforms(gpuContext)
        getProgram(
                StringBasedCodeSource("mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()),
                StringBasedCodeSource("simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()), null, Defines(), uniforms
        )
    }
    override val heightMappingFirstPassProgram = getFirstPassHeightMappingProgram()

    override fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines, uniforms: Uniforms?): ComputeProgram {
        return gpuContext.invoke {
            val program = ComputeProgram(this, codeSource, defines)
            programsCache.add(program)
            eventBus.register(program)
            program
        }
    }

    override fun <T : Uniforms> getProgram(vertexShaderSource: CodeSource,
                                           fragmentShaderSource: CodeSource?,
                                           geometryShaderSource: CodeSource?,
                                           defines: Defines,
                                           uniforms: T): Program<T> {

        return getProgram(vertexShaderSource, fragmentShaderSource, geometryShaderSource, null, null, defines, uniforms)
    }

    override fun <T : Uniforms> getProgram(vertexShaderSource: CodeSource,
                                           fragmentShaderSource: CodeSource?,
                                           geometryShaderSource: CodeSource?,
                                           tesselationControlShaderSource: CodeSource?,
                                           tesselationEvaluationShaderSource: CodeSource?,
                                           defines: Defines,
                                           uniforms: T): Program<T> {

        return gpuContext.invoke {
            Program(
                programManager = this,
                vertexShader = VertexShader(this, vertexShaderSource, defines),
                tesselationControlShader = tesselationControlShaderSource?.let { TesselationControlShader(this, it, defines) },
                tesselationEvaluationShader = tesselationEvaluationShaderSource?.let { TesselationEvaluationShader(this, it, defines) },
                geometryShader = geometryShaderSource?.let { GeometryShader(this, it, defines) },
                fragmentShader = fragmentShaderSource?.let { FragmentShader(this, it, defines) },
                defines = defines,
                uniforms = uniforms
            ).apply {
                programsCache.add(this)
                eventBus.register(this)
            }
        }
    }

    override fun getComputeProgram(codeSource: CodeSource) = gpuContext.invoke { ComputeProgram(this, ComputeShader(this, codeSource)) }

    var programsSourceCache: WeakHashMap<Shader, Int> = WeakHashMap()
    override fun update(deltaSeconds: Float) {
        if(config.debug.isUseFileReloading) {
            programsCache.forEach { program ->
                program.shaders.forEach { shader ->
                    if (shader.source is StringBasedCodeSource || shader.source is WrappedCodeSource || shader.source is ReloadableCodeSource) {
                        programsSourceCache.putIfAbsent(shader, shader.source.source.hashCode())
                        if (shader.source.hasChanged(programsSourceCache[shader]!!)) {
                            program.reload()
                            programsSourceCache[shader] = shader.source.source.hashCode()
                        }
                    }
                }
            }
        }
    }

    override fun CodeSource.toResultingShaderSource(defines: Defines): String {
        return gpuContext.getOpenGlVersionsDefine() +
                gpuContext.getOpenGlExtensionsDefine() +
                defines.toString() +
                ShaderDefine.getGlobalDefinesString(config) +
                Shader.replaceIncludes(config.directories.engineDir, source, 0).left
    }
}

private fun ProgramManager<*>.getFirstPassHeightMappingProgram(): Program<FirstPassUniforms> = getProgram(
    vertexShaderSource = vertexShaderSource,
    tesselationControlShaderSource = tesselationControlShaderSource,
    tesselationEvaluationShaderSource = tesselationEvaluationShaderSource,
    geometryShaderSource = geometryShaderSource,
    fragmentShaderSource = fragmentShaderSource,
    defines = Defines(),
    uniforms = StaticFirstPassUniforms(gpuContext)
)

val vertexShaderSource = StringBasedCodeSource(
    "tesselation",
    """
        uniform int indirect = 1;
        uniform int entityIndex = 0;
        
        //include(globals_structs.glsl)
        
        layout(std430, binding=1) buffer _materials {
            Material materials[100];
        };
        layout(std430, binding=3) buffer _entities {
            Entity entities[2000];
        };
        layout(std430, binding=4) buffer _entityOffsets {
            int entityOffsets[2000];
        };
        layout(std430, binding=7) buffer _vertices {
            VertexPacked vertices[];
        };
        out vec3 WorldPos_CS_in;
        out vec2 TexCoord_CS_in;
        out vec3 Normal_CS_in;
        out Entity entity_CS_in;
        
        void main()
        {
            int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
            if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }
        
            Entity entity = entities[entityBufferIndex];
            entity_CS_in = entity;
            Material material = materials[entity.materialIndex];
            
            VertexPacked vertex = vertices[gl_VertexID];
            
            WorldPos_CS_in = (entity.modelMatrix * vec4(vertex.position.xyz, 1.0)).xyz;
            if(material.useWorldSpaceXZAsTexCoords == 1) {
                vertex.texCoord.xy = WorldPos_CS_in.xz;
            }
            TexCoord_CS_in = vertex.texCoord.xy * material.uvScale;
            Normal_CS_in = (entity.modelMatrix * vec4(vertex.normal.xyz, 0.0)).xyz;
        } 
    """.trimIndent()
)
val tesselationControlShaderSource = StringBasedCodeSource(
    "tesselation",
    """
            layout (vertices = 3) out;
            uniform vec3 eyePosition;
            
            //include(globals_structs.glsl)
            
            layout(std430, binding=1) buffer _materials {
                Material materials[100];
            };
            layout(std430, binding=3) buffer _entities {
                Entity entities[2000];
            };
            
            // attributes of the input CPs
            in vec3 WorldPos_CS_in[];
            in vec2 TexCoord_CS_in[];
            in vec3 Normal_CS_in[];
            in Entity entity_CS_in[];

            // attributes of the output CPs
            out vec3 WorldPos_ES_in[];
            out vec2 TexCoord_ES_in[];
            out vec3 Normal_ES_in[];
            out Entity entity_ES_in[];
            
            float GetTessLevel(float Distance0, float Distance1)
            {
                float AvgDistance = (Distance0 + Distance1) / 2.0;

                if (AvgDistance <= 2.0) {
                    return 10.0;
                }
                else if (AvgDistance <= 500.0) {
                    return 7.0;
                }
                else {
                    return 3.0;
                }
            }
            
            float getTesselationLevel(float Distance0, float Distance1, Material material) {
                float d = (Distance0 + Distance1) / 2.0f;
                
                if(d < 10) return gl_MaxTessGenLevel;
                float lodFactor = d / material.lodFactor;
                lodFactor = 1-clamp(lodFactor, 0.0f, 1.0f);
                return mix(3, gl_MaxTessGenLevel, pow(lodFactor, 2));
                
                if(d < 10) return gl_MaxTessGenLevel;
                if(d < 50) return 32;
                if(d < 100) return 16;
                if(d < 200) return 8;
                if(d < 400) return 4;
                return 3;
            }
            
            void main()
            {
                Material material_CS_in = materials[entity_CS_in[0].materialIndex];
                // Set the control points of the output patch
                TexCoord_ES_in[gl_InvocationID] = TexCoord_CS_in[gl_InvocationID];
                Normal_ES_in[gl_InvocationID] = Normal_CS_in[gl_InvocationID];
                WorldPos_ES_in[gl_InvocationID] = WorldPos_CS_in[gl_InvocationID];
                entity_ES_in[gl_InvocationID] = entity_CS_in[gl_InvocationID];
                
                // Calculate the distance from the camera to the three control points
                float EyeToVertexDistance0 = distance(eyePosition, WorldPos_ES_in[0]);
                float EyeToVertexDistance1 = distance(eyePosition, WorldPos_ES_in[1]);
                float EyeToVertexDistance2 = distance(eyePosition, WorldPos_ES_in[2]);

                // Calculate the tessellation levels
                gl_TessLevelOuter[0] = getTesselationLevel(EyeToVertexDistance1, EyeToVertexDistance2, material_CS_in);
                gl_TessLevelOuter[1] = getTesselationLevel(EyeToVertexDistance2, EyeToVertexDistance0, material_CS_in);
                gl_TessLevelOuter[2] = getTesselationLevel(EyeToVertexDistance0, EyeToVertexDistance1, material_CS_in);
                gl_TessLevelInner[0] = gl_TessLevelOuter[2];
                
            } 
        """.trimIndent()
)

val tesselationEvaluationShaderSource = object: ReloadableCodeSource {
    override val name: String = "tesselation"
    override val source: String
        get() = """
    //                layout(triangles, equal_spacing, ccw) in;
                layout(triangles, fractional_even_spacing, ccw) in;
                #ifdef BINDLESSTEXTURES
                #else
                layout(binding=3) uniform sampler2D displacementMap;
                uniform bool hasDisplacementMap = false;
                layout(binding=4) uniform sampler2D heightMap;
                uniform bool hasHeightMap = false;
    
                #endif
                //include(globals_structs.glsl)
                
                layout(std430, binding=1) buffer _materials {
                    Material materials[100];
                };
                layout(std430, binding=3) buffer _entities {
                    Entity entities[2000];
                };
                
                uniform mat4 viewProjectionMatrix;
                layout(binding=0) uniform sampler2D diffuseMap;
                layout(binding=4) uniform sampler2D gDisplacementMap;
                uniform float gDispFactor = 10.0f;
    
                in vec3 WorldPos_ES_in[];
                in vec2 TexCoord_ES_in[];
                in vec3 Normal_ES_in[];
                in Entity entity_ES_in[];
    
                out vec3 WorldPos_GS_in;
                out vec2 TexCoord_GS_in;
                out vec3 Normal_GS_in;
                flat out Material material_GS_in;
                flat out Entity entity_GS_in;
                
                vec2 interpolate2D(vec2 v0, vec2 v1, vec2 v2)
                {
                    return vec2(gl_TessCoord.x) * v0 + vec2(gl_TessCoord.y) * v1 + vec2(gl_TessCoord.z) * v2;
                }
    
                vec3 interpolate3D(vec3 v0, vec3 v1, vec3 v2)
                {
                    return vec3(gl_TessCoord.x) * v0 + vec3(gl_TessCoord.y) * v1 + vec3(gl_TessCoord.z) * v2;
                } 
                
                void main()
                {
                    Material material = materials[entity_ES_in[0].materialIndex];
                    // Interpolate the attributes of the output vertex using the barycentric coordinates
                    TexCoord_GS_in = interpolate2D(TexCoord_ES_in[0], TexCoord_ES_in[1], TexCoord_ES_in[2]);
                    WorldPos_GS_in = interpolate3D(WorldPos_ES_in[0], WorldPos_ES_in[1], WorldPos_ES_in[2]);
                    material_GS_in = material;
                    entity_GS_in = entity_ES_in[0];
                    Normal_GS_in = interpolate3D(Normal_ES_in[0], Normal_ES_in[1], Normal_ES_in[2]);
                    Normal_GS_in = normalize(Normal_GS_in);
                    // Displace the vertex along the normal
                    if(hasDisplacementMap) {
                        vec4 displacementMapSample = texture(displacementMap, TexCoord_GS_in.xy);
                        WorldPos_GS_in += Normal_GS_in * displacementMapSample.xyz * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;
                        //xxxWorldPos_GS_in += Normal_GS_in * displacementMapSample.y * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;
                        
                    } else if(hasHeightMap) {
                        WorldPos_GS_in += Normal_GS_in * texture(heightMap, TexCoord_GS_in.xy).x * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;
                    }
                    gl_Position = viewProjectionMatrix * vec4(WorldPos_GS_in, 1.0f);
                } 
            """.trimIndent()
}

val geometryShaderSource = StringBasedCodeSource(
    "tesselation", """
        layout ( triangles ) in;
        layout ( triangle_strip, max_vertices = 3 ) out;
        
        uniform mat4 viewProjectionMatrix;
        uniform bool hasDisplacementMap = false;
            
        //include(globals_structs.glsl)
        
        in vec3 WorldPos_GS_in[3];
        in vec2 TexCoord_GS_in[3];
        in vec3 Normal_GS_in[3];
        flat in Material material_GS_in[3];
        flat in Entity entity_GS_in[3];
        
        out vec3 WorldPos_FS_in;
        out vec2 TexCoord_FS_in;
        out vec3 Normal_FS_in;
        flat out Material material_FS_in;
        flat out Entity entity_FS_in;
        
        vec3 calculateFaceNormal(vec3 a, vec3 b, vec3 c) {
            vec3 dir = cross(b-a, c-a);
            return normalize(dir);
        }
        
        void main()
        {
            
            for(int i = 0; i < 3; i++) {
                WorldPos_FS_in = WorldPos_GS_in[i];
                TexCoord_FS_in = TexCoord_GS_in[i];
                Normal_FS_in = calculateFaceNormal(WorldPos_GS_in[0], WorldPos_GS_in[1], WorldPos_GS_in[2]);//Normal_GS_in[i];
                material_FS_in = material_GS_in[i];
                entity_FS_in = entity_GS_in[i];
                gl_Position = viewProjectionMatrix * vec4(WorldPos_GS_in[i], 1.0f);
                EmitVertex();
            }
            EndPrimitive();
        }
    """.trimIndent()
)

val fragmentShaderSource = StringBasedCodeSource(
    "tesselation",
    """
            #ifdef BINDLESSTEXTURES
            #else
            layout(binding=0) uniform sampler2D diffuseMap;
            uniform bool hasDiffuseMap = false;
            layout(binding=1) uniform sampler2D normalMap;
            uniform bool hasNormalMap = false;
            layout(binding=2) uniform sampler2D specularMap;
            uniform bool hasSpecularMap = false;
            layout(binding=3) uniform sampler2D displacementMap;
            uniform bool hasDisplacementMap = false;
            layout(binding=4) uniform sampler2D heightMap;
            uniform bool hasHeightMap = false;
            ////
            layout(binding=7) uniform sampler2D roughnessMap;
            uniform bool hasRoughnessMap = false;
            
            #endif
            
            //include(globals_structs.glsl)
            
            uniform vec3 eyePosition;
            uniform mat4 viewProjectionMatrix;
            uniform mat4 viewMatrix;
            
            in vec3 WorldPos_FS_in;
            in vec2 TexCoord_FS_in;
            in vec3 Normal_FS_in;
            flat in Entity entity_FS_in;
            flat in Material material_FS_in;
            
            layout(location=0)out vec4 out_positionRoughness;
            layout(location=1)out vec4 out_normalAmbient;
            layout(location=2)out vec4 out_colorMetallic;
            layout(location=3)out vec4 out_motionDepthTransparency;
            layout(location=4)out vec4 out_depthAndIndices;
            
            //include(normals.glsl)
            
            vec3 calculateFaceNormal(vec3 a, vec3 b, vec3 c) {
                vec3 dir = cross(b-a, c-a);
                return normalize(dir);
            }
            
            void main()
            {
                vec2 motionVec = vec2(0); // TODO implement me
                float depth = gl_FragDepth; // TODO implement me
                Material material = material_FS_in;
                vec2 UV = TexCoord_FS_in.xy * material.uvScale;
                Entity entity = entity_FS_in;
                vec3 V = -normalize((WorldPos_FS_in.xyz + eyePosition.xyz).xyz);
                out_positionRoughness = vec4((viewMatrix * vec4(WorldPos_FS_in, 1.0f)).xyz, material.roughness);
                out_normalAmbient = vec4((viewMatrix * vec4(Normal_FS_in, 0.0f)).xyz, material.ambient);
                
                vec4 color = vec4(material.diffuse, 1);
                float alpha = material.transparency;
                
                if(hasDiffuseMap) {
                    color = textureLod(diffuseMap, UV, 0);
                    alpha *= color.a;
                }
                if(hasNormalMap) {
                    vec3 PN_world = normalize(perturb_normal(Normal_FS_in, V, UV, normalMap));
                    vec3 PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
//                        out_normalAmbient.xyz = PN_view.xyz;
                    out_normalAmbient.xyz = normalize((viewMatrix * vec4(textureLod(normalMap, UV, 0).xyz, 0)).xyz);
                }
                if(hasRoughnessMap) {
                    float r = textureLod(roughnessMap, UV, 0).x;
                    out_positionRoughness.w = material.roughness*r;
                }
                
                out_colorMetallic = vec4(color.rgb, material.metallic);
                out_motionDepthTransparency = vec4(motionVec,depth,material.transparency);
                out_depthAndIndices = vec4(float(entity.entityIndexWithoutMeshIndex), depth, entity.materialIndex, float(entity.meshIndex));
            } 
        """.trimIndent()
)
