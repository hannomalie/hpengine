//include(globals_structs.glsl)
layout(binding = 0) uniform sampler2D highZ;
layout(binding = 1, r32f) uniform image2D targetImage;

layout(std430, binding=1) buffer _entityCounters {
	int entityCounters[1000];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[10000];
};
layout(std430, binding=4) buffer _offsets {
	int offsets[2000];
};
layout(std430, binding=5) buffer _drawCommandsSource {
	DrawCommand drawCommandsSource[1000];
};
layout(std430, binding=9) buffer _visibility {
	int visibility[1000];
};

uniform int maxDrawCommands = 0;
uniform mat4 viewProjectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

void main(){
	uint invocationId = gl_VertexID;
	if(invocationId < maxDrawCommands)
	{
        uint indexBefore = invocationId;
        int offset = offsets[indexBefore];
        DrawCommand command = drawCommandsSource[indexBefore];

        int instancesBaseOffset = 0;
        for(int i = 0; i < indexBefore; i++) {
            instancesBaseOffset += drawCommandsSource[i].instanceCount;
        }

        for(int i = 0; i < command.instanceCount; i++) {

            bool culledInPhase1 = visibility[instancesBaseOffset+i] == 0;

            if(culledInPhase1) {
                Entity entity = entities[offset+i];

                vec4[2] boundingRect;
                boundingRect[0] = (projectionMatrix*viewMatrix*entity.min);
                boundingRect[0].xyz /= boundingRect[0].w;
                boundingRect[0].xy = boundingRect[0].xy * 0.5 + 0.5;
                boundingRect[1] = (projectionMatrix*viewMatrix*entity.max);
                boundingRect[1].xyz /= boundingRect[1].w;
                boundingRect[1].xy = boundingRect[1].xy * 0.5 + 0.5;

                float ViewSizeX = (boundingRect[1].x-boundingRect[0].x) * 1280/2.0;
                float ViewSizeY = (boundingRect[1].y-boundingRect[0].y) * 720/2.0;
                float LOD = ceil( log2( max( ViewSizeX, ViewSizeY ) / 2.0 ) );
    //            float LOD = ceil( log2( max( ViewSizeX, ViewSizeY ) ) );

                vec4 occluded;
                occluded.r = (textureLod(highZ, vec2(boundingRect[0].xy), LOD).r);
                occluded.g = (textureLod(highZ, vec2(boundingRect[1].xy), LOD).r);
                occluded.b = (textureLod(highZ, vec2(boundingRect[0].x, boundingRect[1].y), LOD).r);
                occluded.a = (textureLod(highZ, vec2(boundingRect[1].x, boundingRect[0].y), LOD).r);

                float maxDepthSample = max(max(occluded.x, occluded.y), max(occluded.z, occluded.w));
                bool minOccluded = boundingRect[0].z > maxDepthSample;
                bool maxOccluded = boundingRect[1].z > maxDepthSample;
                bool allOccluded = minOccluded && maxOccluded;//clamp(min(boundingRect[0].z, boundingRect[1].z), 0, 1) > maxDepthSample;//minOccluded && maxOccluded;

                vec4 color = allOccluded ? vec4(0,1,0,0) : vec4(1,1,0,0);

                visibility[instancesBaseOffset+i] = allOccluded ? 0 : 1;
                if(!allOccluded){
    //                imageStore(targetImage, ivec2(vec2(1280/2, 720/2)*boundingRect[0].xy), color);
    //                imageStore(targetImage, ivec2(vec2(1280/2, 720/2)*boundingRect[1].xy), color);
    //                uint indexAfter = atomicAdd(entitiesTargetCounter, 1);
    //                entitiesTarget[indexAfter] = entity;
                    atomicAdd(entityCounters[indexBefore], 1);
                }
            } else {
                // sets entities culled in phase 1 to invisible for phase 2
                visibility[instancesBaseOffset+i] = 0;
            }
        }
	}
}