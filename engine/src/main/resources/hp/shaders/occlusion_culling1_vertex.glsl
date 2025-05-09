//include(globals_structs.glsl)
layout(binding = 0) uniform sampler2D highZ;
layout(binding = 1, rgba8) uniform image2D targetImage;

layout(std430, binding=1) buffer _entityCounts {
	coherent int entityCounts[2000];
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

layout(std430, binding=9) buffer _visibility {
	int visibility[1000];
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

uniform bool useFrustumCulling = true;
uniform bool useOcclusionCulling = true;

bool pointInBox(vec3 p, vec3 min, vec3 max) {
    return all(lessThanEqual(p, max)) && all(greaterThanEqual(p, min));
}

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
        planes[i].n = normalize(planes[i].n);
    }
    return planes;
}

float distanceToPlane(Plane plane, vec3 point) {
    return dot(vec4(plane.n, plane.d), vec4(point,1.0));
}
bool in_frustum(mat4 M, vec3 min, vec3 max) {
        if(pointInBox(camPosition, min, max)) {
            return true;
        }
//        vec3[8] aabb;
//        aabb[0] = max.xyz;
//        aabb[1] = min.xyz;
//        aabb[2] = vec3(min.xy, max.z);
//        aabb[3] = vec3(min.x, max.y, min.z);
//        aabb[4] = vec3(max.x, min.yz);
//
//        aabb[5] = vec3(max.xy, min.z);
//        aabb[6] = vec3(max.x, min.y, max.z);
//        aabb[7] = vec3(min.x, max.yz);

//        int counter = 0;
//        for(int i = 0; i < 8; i++) {
//            vec4 pClip = M * vec4(aabb[i], 1.);
//
//            bool xInFrustum = abs(pClip.x) < pClip.w;
//            bool yInFrustum = abs(pClip.y) < pClip.w;
//            bool zInFrustum = 0 < pClip.z && pClip.z < pClip.w;
//
//            if(xInFrustum &&
//               yInFrustum &&
//               zInFrustum) {
//                  counter++;
//            }
//        }
//        return counter > 0;

        const int INSIDE = 0;
        const int OUTSIDE = 1;
        const int INTERSECT = 2;

        Plane[6] pl = extractPlanes(M);
        int ret = INSIDE;
        vec3 vmin, vmax;

       for(int i = 0; i < 6; ++i) {
         Plane plane = pl[i];

          // X axis
          if(plane.n.x > 0) {
             vmin.x = min.x;
             vmax.x = max.x;
          } else {
             vmin.x = max.x;
             vmax.x = min.x;
          }
          // Y axis
          if(plane.n.y > 0) {
             vmin.y = min.y;
             vmax.y = max.y;
          } else {
             vmin.y = max.y;
             vmax.y = min.y;
          }
          // Z axis
          if(plane.n.z > 0) {
             vmin.z = min.z;
             vmax.z = max.z;
          } else {
             vmin.z = max.z;
             vmax.z = min.z;
          }
          if(dot(plane.n, vmin) + plane.d > 0)
             return false;
          if(dot(plane.n, vmax) + plane.d >= 0)
             ret = INTERSECT;
       }

        return ret == INSIDE || ret == INTERSECT;
    }
//bool in_frustum(mat4 M, vec3 p) {
//        vec4 Pclip = M * vec4(p, 1.);
//        return abs(Pclip.x) < Pclip.w &&
//               abs(Pclip.y) < Pclip.w &&
//               0 < Pclip.z &&
//               Pclip.z < Pclip.w;
//    }

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

bool in_frustumXXX(mat4 M, vec3 p) {
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

//        bool inFrustum = in_frustum(viewProjectionMatrix, entity.min, entity.max);

        vec3 span = (entity.max - entity.min);
//        bool inFrustum = sphereVisible(viewProjectionMatrix, vec4(entity.min + 0.5 * span, 0.5 * span.x));

//        bool inFrustum = boxInFrustum(viewProjectionMatrix, entity.min, entity.max);
//        bool inFrustum = (testPoint(viewProjectionMatrix, entity.min) && testPoint(viewProjectionMatrix, entity.max));

        bool inFrustum = (
                        in_frustumXXX(viewProjectionMatrix, entity.max)
                        || in_frustumXXX(viewProjectionMatrix, entity.min)
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.min.xy, entity.max.z))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.min.x, entity.max.y, entity.max.z))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.max.x, entity.min.y, entity.max.z))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.max.x, entity.max.yz))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.max.xy, entity.min.z))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.max.x, entity.min.y, entity.max.z))
                        || in_frustumXXX(viewProjectionMatrix, vec3(entity.min + 0.5*entity.max)) //TODO: Better handling large objects, this culse falsely sometimes
                        );



        vec4[2] boundingRect;
        boundingRect[0] = (viewProjectionMatrix * vec4(entity.min, 1.0f));
        boundingRect[1] = (viewProjectionMatrix * vec4(entity.max, 1.0f));
        boundingRect[0].xyz /= boundingRect[0].w;
        boundingRect[1].xyz /= boundingRect[1].w;
        boundingRect[0].xy = boundingRect[0].xy * 0.5 + 0.5;
        boundingRect[1].xy = boundingRect[1].xy * 0.5 + 0.5;

        float ViewSizeX = (boundingRect[1].x-boundingRect[0].x) * (1920/2.0);
        float ViewSizeY = (boundingRect[1].y-boundingRect[0].y) * (1080/2.0);
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

        bool isVisible = true;

        if(useFrustumCulling) {
            isVisible = isVisible && inFrustum;
        }
        if(useOcclusionCulling) {
            isVisible = isVisible && !allOccluded;
        }

//        vec4 color = allOccluded ? vec4(1,0,0,0) : vec4(0,1,0,0);
//        ivec2 texCoordsMin = ivec2(vec2(imageSize(targetImage)) * boundingRect[0].xy);
//        ivec2 texCoordsMax = ivec2(vec2(imageSize(targetImage)) * boundingRect[1].xy);
//        imageStore(targetImage, ivec2(texCoordsMin), minOccluded ? vec4(1,0,0,0) : vec4(0,1,0,0));//vec4(0,0,0,(textureLod(highZ, vec2(boundingRect[0].xy), LOD).r)));
//        imageStore(targetImage, ivec2(texCoordsMax), maxOccluded ? vec4(1,0,0,0) : vec4(0,1,0,0));

        visibility[instancesBaseOffset+invocationIndex] = isVisible ? 1 : 0;
        if(isVisible) {
            atomicAdd(entityCounts[commandIndex], 1);
        }
	}
}