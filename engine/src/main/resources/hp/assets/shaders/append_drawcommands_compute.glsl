layout(local_size_x = 1, local_size_y = 1024, local_size_z = 1) in;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _entityCounts {
	int entityCounts[2000];
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
layout(std430, binding=12) buffer _commandEntityOffsets {
	int commandEntityOffsets[2000];
};
layout(std430, binding=13) buffer _currentCompactedPointers {
	coherent uint currentCompactedPointers[2000];
};
uniform int maxDrawCommands;
uniform int threadCount;

shared uint instanceCounterPerCommand; // no initialization for shared memory, read https://www.khronos.org/opengl/wiki/Compute_Shader
/**
    THIS IS FUCKED UP; THIS SHADER IS SOMEHOW EXECUTED TWICE
    AND THAT SIMPLY FUCKS UP EVERYTHING. UNUSABLE.
**/
void main()
{
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    uint commandIndex = gl_GlobalInvocationID.x;//storePos.x;
    if(commandIndex > maxDrawCommands) { return; }
    uint instanceIndexBeforeMultiply = gl_LocalInvocationID.y;//storePos.y;

    DrawCommand sourceCommand = drawCommandsSource[commandIndex];
    int noOfVisibleInstances = entityCounts[commandIndex];

    int targetCommandIndex = 0;
    int visibilityBufferOffset = 0;
    for(int i = 0; i < commandIndex; i++) {
        visibilityBufferOffset += drawCommandsSource[i].instanceCount;
        if(entityCounts[i] > 0) {
            targetCommandIndex++;
        }
    }


    for(int i = 0; i < 10*1024; i+=1024)
    {
        uint instanceIndex = instanceIndexBeforeMultiply + i;
        if(instanceIndex > sourceCommand.instanceCount) { return; }
        if(instanceIndex == 0) {
            instanceCounterPerCommand = 0;
            if(noOfVisibleInstances > 0) {
                sourceCommand.instanceCount = noOfVisibleInstances;
                drawCommandsTarget[targetCommandIndex] = sourceCommand;
                atomicAdd(drawCount, 1);
            }
        }
//        barrier();
        memoryBarrierShared();

        bool visible = visibility[visibilityBufferOffset + instanceIndex] == 1;
        if(visible)
        {
            int compactedBufferOffset = 0;
            for(int i = 0; i < commandIndex; i++) {
                compactedBufferOffset += entityCounts[i];
            }

            uint entityBufferSource = offsetsSource[commandIndex] + instanceIndex;
            uint targetInstanceIndex = atomicAdd(instanceCounterPerCommand, 1);
            uint compactedEntityBufferIndex = compactedBufferOffset + targetInstanceIndex;

            entitiesCompacted[compactedEntityBufferIndex] = entities[entityBufferSource];

            if(instanceIndex == 0) {
                offsetsTarget[targetCommandIndex] = compactedBufferOffset;
            }
        }

        barrier();
        if(instanceIndex == 0) {
            atomicAdd(entitiesCompactedCounter, noOfVisibleInstances);
//            debug
            currentCompactedPointers[commandIndex] = instanceCounterPerCommand;
        }
    }
}
