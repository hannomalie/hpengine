vec3 entityMin = entity.min;
vec3 entityMax = entity.max;
int cellCount = 10;
ivec3 cellSize = ivec3((entityMax - entityMin).xy/float(cellCount), 110);
if(material.worldSpaceTexCoords == 2) {
    cellSize = ivec3(110, (entityMax - entityMin).yz/float(cellCount));
}
int indexX = int((position_world.x - entityMin.x)/cellSize.x);
int indexY = int((position_world.y - entityMin.y)/cellSize.y);
int indexZ = int((position_world.z - entityMin.z)/cellSize.z);
int index = indexX + indexY + indexZ;

vec3 min = entityMin + vec3(indexX * cellSize.x, indexY * cellSize.y, indexZ * cellSize.z);
vec3 max = min + cellSize;

vec3 _eyePosition = (inverse(viewMatrix) * vec4(0,0,0,1)).xyz;
V = normalize(position_world.xyz - _eyePosition);
vec3 intersectionPoint = getIntersectionPoint(_eyePosition, V, min, max);

vec3 eyePositionBack = intersectionPoint + (V * 2*length(cellSize));

vec3 intersectionPointBack = getIntersectionPoint(eyePositionBack, -V, min, max);

intersectionPointBack = _eyePosition + V*intersectAABB(_eyePosition, V, min, max).y;

vec3 center = min + vec3(55);
vec3 boxProjected = normalize(intersectionPointBack - center.xyz);
if(material.worldSpaceTexCoords != 2) {
    V.z *= -1;
}
vec3 newV = boxProjectFar(position_world.xyz, V, min, max);
mat4 rotationMatrix = rotationMatrix(vec3(0,1,0), 1.57 * (index % 10));
newV = (rotationMatrix * vec4(newV, 0)).xyz;


vec3 positionInCell = (ivec3(position_world.xyz - entityMin)%ivec3(cellSize)) + fract(position_world.xyz);
float windowBorderSize = cellSize.x - 5;
if(material.worldSpaceTexCoords == 2) {
    windowBorderSize = cellSize.z - 5;
}

bool isWindow = (positionInCell.x > 5 && positionInCell.x < windowBorderSize)
    && (positionInCell.y > 5 && positionInCell.y < windowBorderSize);
if(material.worldSpaceTexCoords == 2) {
    isWindow = (positionInCell.z > 5 && positionInCell.z < windowBorderSize)
        && (positionInCell.y > 5 && positionInCell.y < windowBorderSize);
}

uint dominantAxis = getMaxIndex(newV);
vec2 uv;
if(dominantAxis == 0) {
    uv = (vec2(-newV.z, -newV.y)/abs(newV.x)+vec2(1))/2;
} else if(dominantAxis == 1) {
    uv = (vec2(-newV.x, newV.z)/abs(newV.y)+vec2(1))/2;
} else {
    uv = (vec2(-newV.x, newV.y)/abs(newV.z)+vec2(1))/2;
}

if(isWindow) {
    if(index%2 == 0) {
        out_color.rgba = vec4(texture(environmentMap, newV).rgb, 1);
    } else {
        out_color.rgba = vec4(texture(environmentMap0, newV).rgb, 1);
    }
//    out_color.rgba = vec4(texture(diffuseMap, uv).rgb, 1);

    vec3 boxProjectedV = boxProjectFar(position_world.xyz, reflect(V, normal_world), vec3(-2000), vec3(2000));
    out_color.rgb += (1-material.roughness) * texture(environmentMap1, boxProjectedV).rgb;
} else {
    if(material.worldSpaceTexCoords == 2) {
        out_color.rgba = vec4(texture(normalMap, position_world.zy * 0.1).rgb, 1);
    } else {
        out_color.rgba = vec4(texture(normalMap, position_world.xy * 0.1).rgb, 1);
    }
}