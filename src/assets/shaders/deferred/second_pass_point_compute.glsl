#version 430 core

#define MAX_LIGHTS 1024
#define MAX_LIGHTS_PER_TILE 40

#define WORK_GROUP_SIZE 16

struct PointLight
{
    vec3 position;
    float radius;
    vec3 color;
    float intensity;
};

layout (binding = 0, rgba32f) uniform writeonly image2D outTexture;
layout (binding = 1, rgba32f) uniform readonly image2D normalDepth;
layout (binding = 2, rgba32f) uniform readonly image2D diffuse;
layout (binding = 3, rgba32f) uniform readonly image2D specular;
layout (binding = 4, rgba32f) uniform readonly image2D glowMatID;

layout (std430, binding = 5) buffer BufferObject
{
    PointLight pointLights[];
};

uniform mat4 view;
uniform mat4 proj;
uniform mat4 viewProj;
uniform mat4 invViewProj;
uniform mat4 invProj;
uniform vec2 framebufferDim;

layout (local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

shared uint minDepth = 0xFFFFFFFF;
shared uint maxDepth = 0;
shared uint pointLightIndex[MAX_LIGHTS];
shared uint pointLightCount = 0;

vec3 ReconstructWP(float z, vec2 uv_f)
{
    vec4 sPos = vec4(uv_f * 2.0 - 1.0, z, 1.0);
    sPos = invViewProj * sPos;

    return (sPos.xyz / sPos.w);
}

vec4 ConvertProjToView( vec4 p )
{
    p = invProj * p;
    p /= p.w;
    return p;
}

// calculate the number of tiles in the horizontal direction
uint GetNumTilesX()
{
    return uint(( ( 1280 + WORK_GROUP_SIZE - 1 ) / float(WORK_GROUP_SIZE) ));
}

// calculate the number of tiles in the vertical direction
uint GetNumTilesY()
{
    return uint(( ( 720 + WORK_GROUP_SIZE - 1 ) / float(WORK_GROUP_SIZE) ));
}


vec4 CreatePlaneEquation( vec4 b, vec4 c )
{
    vec4 n;

    // normalize(cross( b.xyz-a.xyz, c.xyz-a.xyz )), except we know "a" is the origin
     n.xyz = normalize(cross( b.xyz, c.xyz ));

    // -(n dot a), except we know "a" is the origin
    n.w = 0;

    return n;
}

float GetSignedDistanceFromPlane( vec4 p, vec4 eqn )
{
    // dot( eqn.xyz, p.xyz ) + eqn.w, , except we know eqn.w is zero 
    // (see CreatePlaneEquation above)
    return dot( eqn.xyz, p.xyz );
}

vec4 CalculateLighting( PointLight p, vec3 wPos, vec3 wNormal, vec4 wSpec, vec4 wGlow)
{
    vec3 direction = p.position - wPos;

    if(length(direction) > p.radius)
        return vec4(0.0f, 0.0f, 0.0f, 0.0f);

    float attenuation = 1.0f - length(direction) / (p.radius);
    direction = normalize(direction);
    float diffuseFactor = max(0.0f, dot(direction, wNormal)) * attenuation;
    return vec4(p.color.xyz, 0.0f) * diffuseFactor * p.intensity;
}


void main()
{
        ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
        vec2 tilePos = vec2(gl_WorkGroupID.xy * gl_WorkGroupSize.xy) / vec2(1280, 720);

        vec4 normalColor = imageLoad(normalDepth, pixelPos);

        float d = normalColor.w;

        uint depth = uint(d * 0xFFFFFFFF);

        atomicMin(minDepth, depth);
        atomicMax(maxDepth, depth);

        barrier();

        float minDepthZ = float(minDepth / float(0xFFFFFFFF));
        float maxDepthZ = float(maxDepth / float(0xFFFFFFFF));

        vec4 frustumEqn[4];
        uint pxm = WORK_GROUP_SIZE * gl_WorkGroupID.x;
        uint pym = WORK_GROUP_SIZE * gl_WorkGroupID.y;
        uint pxp = WORK_GROUP_SIZE * (gl_WorkGroupID.x + 1);
        uint pyp = WORK_GROUP_SIZE * (gl_WorkGroupID.y + 1);

        uint uWindowWidthEvenlyDivisibleByTileRes = WORK_GROUP_SIZE * GetNumTilesX();
        uint uWindowHeightEvenlyDivisibleByTileRes = WORK_GROUP_SIZE * GetNumTilesY();

        vec4 frustum[4];
        frustum[0] = ConvertProjToView( vec4( pxm / float(uWindowWidthEvenlyDivisibleByTileRes) * 2.0f - 1.0f, (uWindowHeightEvenlyDivisibleByTileRes - pym) / float(uWindowHeightEvenlyDivisibleByTileRes) * 2.0f - 1.0f, 1.0f, 1.0f) );
        frustum[1] = ConvertProjToView( vec4( pxp / float(uWindowWidthEvenlyDivisibleByTileRes) * 2.0f - 1.0f, (uWindowHeightEvenlyDivisibleByTileRes - pym) / float(uWindowHeightEvenlyDivisibleByTileRes) * 2.0f - 1.0f, 1.0f, 1.0f) );
        frustum[2] = ConvertProjToView( vec4( pxp / float(uWindowWidthEvenlyDivisibleByTileRes) * 2.0f - 1.0f, (uWindowHeightEvenlyDivisibleByTileRes - pyp) / float(uWindowHeightEvenlyDivisibleByTileRes) * 2.0f - 1.0f, 1.0f ,1.0f) );
        frustum[3] = ConvertProjToView( vec4( pxm / float(uWindowWidthEvenlyDivisibleByTileRes) * 2.0f - 1.0f, (uWindowHeightEvenlyDivisibleByTileRes - pyp) / float(uWindowHeightEvenlyDivisibleByTileRes) * 2.0f - 1.0f, 1.0f, 1.0f) );

        for (int i = 0; i < 4; i++)
            frustumEqn[i] = CreatePlaneEquation(frustum[i], frustum[(i+1) & 3]);

        barrier();

        int threadsPerTile = WORK_GROUP_SIZE * WORK_GROUP_SIZE;

        for (uint i = 0; i < MAX_LIGHTS; i+= threadsPerTile)
        {
            uint il = gl_LocalInvocationIndex + i;

            if (il < MAX_LIGHTS)
            {
                PointLight p = pointLights[il];

                vec4 viewPos = view * vec4(p.position, 1.0f);
                float r = p.radius;

                if (viewPos.z + minDepthZ < r && viewPos.z - maxDepthZ < r)
                {

                if( ( GetSignedDistanceFromPlane( viewPos, frustumEqn[0] ) < r ) &&
                    ( GetSignedDistanceFromPlane( viewPos, frustumEqn[1] ) < r ) &&
                    ( GetSignedDistanceFromPlane( viewPos, frustumEqn[2] ) < r ) &&
                    ( GetSignedDistanceFromPlane( viewPos, frustumEqn[3] ) < r) )

                    {
                        uint id = atomicAdd(pointLightCount, 1);
                        pointLightIndex[id] = il;
                    }
                }

            }
        }

        barrier();

        vec4 diffuseColor = imageLoad(diffuse, pixelPos);
        vec4 specularColor = imageLoad(specular, pixelPos);
        vec4 glowColor = imageLoad(glowMatID, pixelPos);

        vec2 uv = vec2(pixelPos.x / 1280.0f, pixelPos.y / 720.0f);

        vec3 wp = ReconstructWP(d, uv);
        vec4 color = vec4(0.0f, 0.0f, 0.0f, 1.0f);

        for (int i = 0; i < pointLightCount; i++)
        {
            color += CalculateLighting( pointLights[pointLightIndex[i]], wp, normalColor.xyz, specularColor, glowColor);
        }

        barrier();

        if (gl_LocalInvocationID.x == 0 || gl_LocalInvocationID.y == 0 || gl_LocalInvocationID.x == 16 || gl_LocalInvocationID.y == 16)
            imageStore(outTexture, pixelPos, vec4(.2f, .2f, .2f, 1.0f));
        else
        {
            imageStore(outTexture, pixelPos, color);
            //imageStore(outTexture, pixelPos, vec4(maxDepthZ));
            //imageStore(outTexture, pixelPos, vec4(pointLightCount / 128.0f));
            //imageStore(outTexture, pixelPos, vec4(vec2(tilePos.xy), 0.0f, 1.0f));
        }
}