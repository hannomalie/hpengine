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
uniform vec3 camPosition;

bool pointInBox(vec3 p, vec3 min, vec3 max) {
    return all(lessThanEqual(p, max)) && all(greaterThanEqual(p, min));
}
bool in_frustum(mat4 M, vec3 min, vec3 max) {
        if(pointInBox(camPosition, min, max)) {
            return true;
        }
        vec3[8] aabb;
        aabb[0] = max.xyz;
        aabb[1] = min.xyz;
        aabb[2] = vec3(min.xy, max.z);
        aabb[3] = vec3(min.x, max.y, min.z);
        aabb[4] = vec3(max.x, min.yz);

        aabb[5] = vec3(max.xy, min.z);
        aabb[6] = vec3(max.x, min.y, max.z);
        aabb[7] = vec3(min.x, max.yz);

        int counter = 0;
        for(int i = 0; i < 8; i++) {
            vec4 pClip = M * vec4(aabb[i], 1.);

            bool xInFrustum = abs(pClip.x) < pClip.w;
            bool yInFrustum = abs(pClip.y) < pClip.w;
            bool zInFrustum = 0 < pClip.z && pClip.z < pClip.w;

            if(xInFrustum &&
               yInFrustum &&
               zInFrustum) {
                  counter++;
            }
        }
//        if(counter > 0) {
//            return true;
//        }
//
//        vec4 minClip = M * vec4(aabb[0], 1.);
//        vec4 maxClip = M * vec4(aabb[1], 1.);
//        if(minClip.y < -minClip.w && maxClip.y > maxClip.w ||
//           minClip.y > minClip.w && maxClip.y < -maxClip.w ) {
//            counter++;
//        }
//        if(minClip.x < -minClip.w && maxClip.x > maxClip.w ||
//           minClip.x > minClip.w && maxClip.x < -maxClip.w) {
//            counter++;
//        }
        return counter > 0;
    }
//bool in_frustum(mat4 M, vec3 p) {
//        vec4 Pclip = M * vec4(p, 1.);
//        return abs(Pclip.x) < Pclip.w &&
//               abs(Pclip.y) < Pclip.w &&
//               0 < Pclip.z &&
//               Pclip.z < Pclip.w;
//    }

struct Plane {
    vec3 n;
    float d;
};
Plane[6] extractPlanes(mat4 comboMatrix)
{
    Plane[6] planes;
    // left clipping plane:
    planes[0].n.x = comboMatrix[3][0] + comboMatrix[0][0];
    planes[0].n.y = comboMatrix[3][1] + comboMatrix[0][1];
    planes[0].n.z = comboMatrix[3][2] + comboMatrix[0][2];
    planes[0].d   = comboMatrix[3][3] + comboMatrix[0][3];

    // right clipping plane:
    planes[1].n.x = comboMatrix[3][0] - comboMatrix[0][0];
    planes[1].n.y = comboMatrix[3][1] - comboMatrix[0][1];
    planes[1].n.z = comboMatrix[3][2] - comboMatrix[0][2];
    planes[1].d   = comboMatrix[3][3] - comboMatrix[0][3];

    // top clipping plane:
    planes[2].n.x = comboMatrix[3][0] - comboMatrix[1][0];
    planes[2].n.y = comboMatrix[3][1] - comboMatrix[1][1];
    planes[2].n.z = comboMatrix[3][2] - comboMatrix[1][2];
    planes[2].d   = comboMatrix[3][3] - comboMatrix[1][3];

    // bottom clipping plane:
    planes[3].n.x = comboMatrix[3][0] + comboMatrix[1][0];
    planes[3].n.y = comboMatrix[3][1] + comboMatrix[1][1];
    planes[3].n.z = comboMatrix[3][2] + comboMatrix[1][2];
    planes[3].d   = comboMatrix[3][3] + comboMatrix[1][3];

    // near clipping plane:
    planes[4].n.x = comboMatrix[3][0] + comboMatrix[2][0];
    planes[4].n.y = comboMatrix[3][1] + comboMatrix[2][1];
    planes[4].n.z = comboMatrix[3][2] + comboMatrix[2][2];
    planes[4].d   = comboMatrix[3][3] + comboMatrix[2][3];

    // far clipping plane:
    planes[5].n.x = comboMatrix[3][0] - comboMatrix[2][0];
    planes[5].n.y = comboMatrix[3][1] - comboMatrix[2][1];
    planes[5].n.z = comboMatrix[3][2] - comboMatrix[2][2];
    planes[5].d   = comboMatrix[3][3] - comboMatrix[2][3];

    for (int i = 0; i < 6; ++i) {
        float invl = sqrt((planes[i].n.x * planes[i].n.x) + (planes[i].n.y * planes[i].n.y) + (planes[i].n.z * planes[i].n.z));
        planes[i].n.x *= invl;
        planes[i].n.y *= invl;
        planes[i].n.z *= invl;
        planes[i].d *= invl;
    }
    return planes;
}
bool testPoint(Plane plane, vec3 point)
{
    float d = plane.n.x * point.x + plane.n.y * point.y + plane.n.z * point.z + plane.d;

    if (d < 0) {
        return false;
    }

    if (d > 0) {
        return true;
    }

    return true;
}

bool testPoint(mat4 comboMatrix, vec3 point) {
    Plane[6] planes = extractPlanes(comboMatrix);
    int counter = 0;
    for(int i = 0; i < 6; i++) {
        if(testPoint(planes[i], point)) {
            counter++;
        }
    }

    return counter == 6;
}

float distanceToPlane(Plane plane, vec3 point) {
    return dot(vec4(plane.n, plane.d), vec4(point,1.0));
}

bool sphereVisible(mat4 comboMatrix, vec4 centerRadius) {
    Plane[6] planes = extractPlanes(comboMatrix);
    vec3 center = centerRadius.xyz;
    float dist0 = min(distanceToPlane(planes[0], center), distanceToPlane(planes[1], center));
    float dist1 = min(distanceToPlane(planes[2], center), distanceToPlane(planes[3], center));
    float dist2 = min(distanceToPlane(planes[4], center), distanceToPlane(planes[5], center));

    float minPlusRadius = min(dist0, min(dist1, dist2) + centerRadius.w);
    return minPlusRadius > 0;
}
bool boxInFrustum(mat4 viewProjectionMatrix, vec3 min, vec3 max)
{
    Plane[6] planes = extractPlanes(viewProjectionMatrix);
    for( int i=0; i<6; i++ )
    {
        int result = 0;
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(min.x, min.y, min.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(max.x, min.y, min.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(min.x, max.y, min.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(max.x, max.y, min.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(min.x, min.y, max.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(max.x, min.y, max.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(min.x, max.y, max.z, 1.0f) ) < 0.0 )?1:0);
        result += ((dot( vec4(planes[i].n, planes[i].d), vec4(max.x, max.y, max.z, 1.0f) ) < 0.0 )?1:0);
        if( result==8 ) return false;
    }

    return true;
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
        mat4 mvp = projectionMatrix*viewMatrix*entity.modelMatrix;

        bool inFrustum = in_frustum(viewProjectionMatrix, entity.min.xyz, entity.max.xyz);

        vec3 span = (entity.max.xyz - entity.min.xyz);
//        bool inFrustum = sphereVisible(viewProjectionMatrix, vec4(entity.min.xyz + 0.5 * span, 0.5 * span.x));
//        bool inFrustum = boxInFrustum(viewProjectionMatrix, entity.min.xyz, entity.max.xyz);
//        bool inFrustum = (testPoint(viewProjectionMatrix, entity.min.xyz) && testPoint(viewProjectionMatrix, entity.max.xyz));

        vec4[2] boundingRect;
        vec4[2] minMaxView;
        minMaxView[0] = viewMatrix*entity.min;
        minMaxView[1] = viewMatrix*entity.max;
//        bool inFrustum = (testPoint(viewProjectionMatrix, minMaxView[0].xyz) && testPoint(viewProjectionMatrix, minMaxView[1].xyz));

        boundingRect[0] = (projectionMatrix*minMaxView[0]);
        boundingRect[1] = (projectionMatrix*minMaxView[1]);
        boundingRect[0].xyz /= max(boundingRect[0].w, 0.00001);
        boundingRect[1].xyz /= max(boundingRect[1].w, 0.00001);

        boundingRect[0].xy = boundingRect[0].xy * 0.5 + 0.5;
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
        bool allOccluded = minOccluded && maxOccluded;

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

//        isVisible = true;
        visibility[instancesBaseOffset+invocationIndex] = isVisible ? 1 : 0;
        if(isVisible) {
            atomicAdd(entityCounts[commandIndex], 1);
        }
	}
}