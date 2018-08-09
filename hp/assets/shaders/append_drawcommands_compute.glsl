#define WORK_GROUP_SIZE 1024

layout(local_size_x = WORK_GROUP_SIZE) in;

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

shared uint instanceCounterPerCommand = 0;
shared uint[] instanceIndicesToAppend;

void main()
{
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    uint commandIndex = storePos.y;
    if(commandIndex > maxDrawCommands) { return; }

    DrawCommand sourceCommand = drawCommandsSource[commandIndex];

    int targetCommandIndex = 0;
    int visibilityBufferOffset = 0;
    for(int i = 0; i < commandIndex; i++) {
        visibilityBufferOffset += drawCommandsSource[i].instanceCount;
        if(entityCounts[i] > 0) {
            targetCommandIndex++;
        }
    }


    for(int i = 0; i < 10*1024; i+=1024) {

        uint instanceIndex = storePos.x + i;
        if(instanceIndex > sourceCommand.instanceCount) { return; }
        if(instanceIndex == 0 && commandIndex == 0 && i == 0) {
            int noOfVisibleInstances = entityCounts[commandIndex];
            if(noOfVisibleInstances > 0) {
                atomicAdd(entitiesCompactedCounter, entityCounts[commandIndex]);
                atomicAdd(drawCount, 1);
                sourceCommand.instanceCount = noOfVisibleInstances;
                drawCommandsTarget[targetCommandIndex] = sourceCommand;
            }
        }

        bool visible = visibility[visibilityBufferOffset + instanceIndex] == 1;
        if(visible)
        {
            uint targetInstanceIndex = atomicAdd(instanceCounterPerCommand, 1);
            int compactedBufferOffset = 0;
            for(int i = 0; i < commandIndex; i++) {
                compactedBufferOffset += entityCounts[i];
            }

            uint entityBufferSource = offsetsSource[commandIndex] + instanceIndex;
    //        uint compactedPointer = atomicAdd(currentCompactedPointers[commandIndex], 1);
            uint compactedEntityBufferIndex = compactedBufferOffset + targetInstanceIndex;//compactedPointer;

            entitiesCompacted[compactedEntityBufferIndex] = entities[entityBufferSource];

            if(instanceIndex == 0) {
                offsetsTarget[targetCommandIndex] = compactedBufferOffset;
            }
    //        DEBUG
    //        commandEntityOffsets[commandIndex] = compactedBufferOffset;
        }

        if(instanceIndex == 0 && commandIndex == 0 && i == 0) {
            barrier();
            currentCompactedPointers[commandIndex] = instanceCounterPerCommand;//sourceCommand.instanceCount;
        }
    }
}
