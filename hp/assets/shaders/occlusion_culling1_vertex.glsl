//include(globals_structs.glsl)
layout(binding = 0) uniform sampler2D highZ;
layout(binding = 1, r32f) uniform image2D targetImage;

layout(std430, binding=1) buffer _entityCounts {
	coherent int entityCounts[2000];
};
layout(std430, binding=2) buffer _drawCount{
	int drawCount;
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};

layout(std430, binding=4) buffer _offsetsSource {
	int offsetsSource[1000];
};

layout(std430, binding=5) buffer _drawCommandsSource {
	DrawCommand drawCommandsSource[1000];
};

layout(std430, binding=7) buffer _drawCommandsTarget {
	DrawCommand drawCommandsTarget[1000];
};

layout(std430, binding=8) buffer _offsetsTarget {
	int offsetsTarget[1000];
};

layout(std430, binding=9) buffer _visibility {
	int visibility[1000];
};
layout(std430, binding=10) buffer _entitiesCompacted {
	Entity entitiesCompacted[2000];
};
layout(std430, binding=11) buffer _entitiesCompactedCounter {
	int entitiesCompactedCounter;
};
layout(std430, binding=12) buffer _bufferOffsets {
	int bufferOffsets[2000];
};
layout(std430, binding=13) buffer _currentCompactedPointers {
	int currentCompactedPointers[2000];
};

uniform int maxDrawCommands = 0;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

bool in_frustum(mat4 M, vec3 p) {
        vec4 Pclip = M * vec4(p, 1.);
        const float bias = 0.;
        return abs(Pclip.x) < Pclip.w + bias &&
               abs(Pclip.y) < Pclip.w + bias &&
               0 < Pclip.z + bias &&
               Pclip.z < Pclip.w + bias;
    }

void main(){
    mat4 viewProjectionMatrix = projectionMatrix*viewMatrix;
	uint commandIndex = gl_VertexID;
	uint invocationIndex = gl_InstanceID;
    DrawCommand command = drawCommandsSource[commandIndex];

	if(commandIndex < maxDrawCommands && invocationIndex < command.instanceCount)
	{

        //////// RESET BUFFER STATE
        if(invocationIndex == 0) {
            if(commandIndex == 0) {
                entitiesCompactedCounter = 0;
            }
            entityCounts[commandIndex] = 0;
            currentCompactedPointers[commandIndex] = 0;
            bufferOffsets[commandIndex] = 0;
        }

        int offset = offsetsSource[commandIndex];

        int instancesBaseOffset = 0;
        for(int i = 0; i < commandIndex; i++) {
            instancesBaseOffset += drawCommandsSource[i].instanceCount;
        }

        Entity entity = entities[offset+invocationIndex];

        bool inFrustum = (
                        in_frustum(viewProjectionMatrix, entity.max.xyz)
                        || in_frustum(viewProjectionMatrix, entity.min.xyz)
                        || in_frustum(viewProjectionMatrix, vec3(entity.min.xy, entity.max.z))
                        || in_frustum(viewProjectionMatrix, vec3(entity.min.x, entity.max.y, entity.max.z))
                        || in_frustum(viewProjectionMatrix, vec3(entity.max.x, entity.min.y, entity.max.z))
                        || in_frustum(viewProjectionMatrix, vec3(entity.max.x, entity.max.yz))
                        || in_frustum(viewProjectionMatrix, vec3(entity.max.xy, entity.min.z))
                        || in_frustum(viewProjectionMatrix, vec3(entity.max.x, entity.min.y, entity.max.z))
                        || in_frustum(viewProjectionMatrix, vec3(entity.min + 0.5*entity.max)) //TODO: Better handling large objects, this culse falsely sometimes
                        );

        vec4[2] boundingRect;
        vec4[2] minMaxView;
        minMaxView[0] = viewMatrix*entity.min;
        minMaxView[1] = viewMatrix*entity.max;

        boundingRect[0] = (projectionMatrix*minMaxView[0]);
        boundingRect[0].xyz /= max(boundingRect[0].w, 0.00001);
        boundingRect[0].xy = boundingRect[0].xy * 0.5 + 0.5;
        boundingRect[1] = (projectionMatrix*minMaxView[1]);
        boundingRect[1].xyz /= max(boundingRect[1].w, 0.00001);
        boundingRect[1].xy = boundingRect[1].xy * 0.5 + 0.5;

        float ViewSizeX = (boundingRect[1].x-boundingRect[0].x) * (1280/2.0);
        float ViewSizeY = (boundingRect[1].y-boundingRect[0].y) * (720/2.0);
        float LOD = ceil( log2( max( ViewSizeX, ViewSizeY ) / 2.0 ) );
//        float LOD = ceil( log2( max( ViewSizeX, ViewSizeY ) ) );

        vec4 occluded;
        occluded.r = (textureLod(highZ, vec2(boundingRect[0].xy), LOD).r);
        occluded.g = (textureLod(highZ, vec2(boundingRect[1].xy), LOD).r);
        occluded.b = (textureLod(highZ, vec2(boundingRect[0].x, boundingRect[1].y), LOD).r);
        occluded.a = (textureLod(highZ, vec2(boundingRect[1].x, boundingRect[0].y), LOD).r);

        float maxDepthSample = max(max(occluded.x, occluded.y), max(occluded.z, occluded.w));
        float bias = 0.f;
        bool minOccluded = boundingRect[0].z > maxDepthSample + bias;
        bool maxOccluded = boundingRect[1].z > maxDepthSample + bias;
        bool allOccluded = false;//minOccluded && maxOccluded;

//                vec4 color = allOccluded ? vec4(0,1,0,0) : vec4(1,0,0,0);
//                ivec2 texCoordsMin = ivec2(vec2(1280/2, 720/2)*boundingRect[0].xy);
//                ivec2 texCoordsMax = ivec2(vec2(1280/2, 720/2)*boundingRect[1].xy);
//	    imageStore(targetImage, texCoordsMin, color);//vec4(0,0,0,(textureLod(highZ, vec2(boundingRect[0].xy), LOD).r)));
//	    imageStore(targetImage, texCoordsMax, color);

        bool isVisible = true;

#ifdef FRUSTUM_CULLING
        isVisible = isVisible && inFrustum;
#endif

#ifdef OCCLUSION_CULLING
        isVisible = isVisible && !allOccluded;
#endif

        visibility[instancesBaseOffset+invocationIndex] = isVisible ? 1 : 0;
        if(isVisible) {
            atomicAdd(entityCounts[commandIndex], 1);
        }
	}
}