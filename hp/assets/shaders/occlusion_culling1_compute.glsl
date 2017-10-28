//include(globals_structs.glsl)
layout(binding = 0) uniform sampler2D highZ;
layout(binding = 1, rgba16f) uniform image2D targetImage;

layout(std430, binding=2) buffer _drawCount {
	uint drawCount;
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
layout(std430, binding=9) buffer _drawCountAfterPhase1 {
	uint drawCountAfterPhase1;
};

#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int maxDrawCount = 0;
uniform mat4 viewProjectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

void main(){
	uint invocationId = gl_GlobalInvocationID.x;
	if(invocationId < maxDrawCount)
	{
        uint indexBefore = invocationId;
        DrawCommand sourceCommand = drawCommandsSource[indexBefore];

        uint indexAfter = atomicAdd(drawCount, 1);
        indexAfter = indexBefore;
        int offset = offsetsSource[indexBefore];
        Entity entity;
        if(sourceCommand.instanceCount > 1){
            entity = entities[offset+1];
        } else {
            entity = entities[offset];
        }

        vec4[2] boundingRect;
        boundingRect[0] = (projectionMatrix*viewMatrix*entity.min);
        boundingRect[0].xyz /= boundingRect[0].w;
        boundingRect[0].xy = boundingRect[0].xy * 0.5 + 0.5;
        boundingRect[1] = (projectionMatrix*viewMatrix*entity.max);
        boundingRect[1].xyz /= boundingRect[1].w;
        boundingRect[1].xy = boundingRect[1].xy * 0.5 + 0.5;
//        boundingRect[0].y = 1-boundingRect[0].y;
//        boundingRect[1].y = 1-boundingRect[1].y;

        float ViewSizeX = (boundingRect[1].x-boundingRect[0].x) * 1280/2;
        float ViewSizeY = (boundingRect[1].y-boundingRect[0].y) * 720/2;
        float LOD = ceil( log2( max( ViewSizeX, ViewSizeY ) / 2.0 ) );

	    vec4 occluded;
	    occluded.r = (textureLod(highZ, vec2(boundingRect[0].xy), LOD).a);
	    occluded.g = (textureLod(highZ, vec2(boundingRect[1].xy), LOD).a);
	    occluded.b = (textureLod(highZ, vec2(boundingRect[0].x, boundingRect[1].y), LOD).a);
	    occluded.a = (textureLod(highZ, vec2(boundingRect[1].x, boundingRect[0].y), LOD).a);

        float maxDepthSample = max(max(occluded.x, occluded.y), max(occluded.z, occluded.w));
        bool minOccluded = boundingRect[0].z > maxDepthSample;
        bool maxOccluded = boundingRect[1].z > maxDepthSample;
        bool allOccluded = minOccluded && maxOccluded;

        vec4 color = allOccluded ? vec4(0,1,0,0) : vec4(1,0,0,0);
//	    imageStore(targetImage, ivec2(vec2(1280/2, 720/2)*boundingRect[0].xy), color);
//	    imageStore(targetImage, ivec2(vec2(1280/2, 720/2)*boundingRect[1].xy), color);

        if(allOccluded)
        {
            sourceCommand = DrawCommand(0,0,0,0,0);
            drawCommandsTarget[indexAfter] = sourceCommand;
//            offsetsTarget[indexAfter] = offset;
        }
        else {
            drawCommandsTarget[indexAfter] = sourceCommand;
            offsetsTarget[indexAfter] = offset;
            atomicAdd(drawCountAfterPhase1, 1);
        }
	}
}