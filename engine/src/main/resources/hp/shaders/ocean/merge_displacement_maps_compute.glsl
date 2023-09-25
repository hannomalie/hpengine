layout (local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

layout (binding = 0, rgba32f) uniform writeonly image2D displacement;
layout (binding = 1) uniform sampler2D displacementX;
layout (binding = 2) uniform sampler2D displacementY;
layout (binding = 3) uniform sampler2D displacementZ;
layout (binding = 4, rgba32f) uniform writeonly image2D normals;
layout (binding = 5, rgba32f) uniform writeonly image2D albedo;
layout (binding = 6, rgba32f) uniform writeonly image2D roughness;
layout (binding = 7) uniform sampler2D debug;

uniform int N = 512;
uniform mat4 viewMatrix;
uniform vec3 diffuseColor = vec3(0.05,0.2,0.8);
uniform float choppiness = 1.0f;
uniform float waveHeight = 1.0f;

const vec2 size = vec2(2.0,0.0);
const ivec3 off = ivec3(-1,0,1);

float jacobian(vec2 dDdx, vec2 dDdy)
{
    return (1.0 + dDdx.x) * (1.0 + dDdy.y) - dDdx.y * dDdy.x;
}

void main(void)
{
    vec3 V = (viewMatrix * vec4(0,0,-1,0)).xyz;
    ivec2 uv = ivec2(gl_GlobalInvocationID.xy);
    ivec2 workGroup = ivec2(gl_WorkGroupID);
    ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
    ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
    vec2 st = vec2(uv) / vec2(N);
    vec2 uvTextureSpace = st;
    vec3 resultingDisplacement = waveHeight * textureLod(displacementY, uvTextureSpace, 0).xyz;
    resultingDisplacement += choppiness * textureLod(displacementX, uvTextureSpace, 0).xyz;
    resultingDisplacement += choppiness * textureLod(displacementZ, uvTextureSpace, 0).xyz;
    resultingDisplacement *= 100f;

    imageStore(displacement, uv, vec4(resultingDisplacement, 1));

    float s01 = waveHeight * textureLod(displacementY, vec2(uv + off.xy)/float(N), 0).x;
    float s21 = waveHeight * textureLod(displacementY, vec2(uv + off.zy)/float(N), 0).x;
    float s10 = waveHeight * textureLod(displacementY, vec2(uv + off.yx)/float(N), 0).x;
    float s12 = waveHeight * textureLod(displacementY, vec2(uv + off.yz)/float(N), 0).x;
    vec3 va = normalize(vec3(size.x,s21-s01, size.y));
    vec3 vb = normalize(vec3(size.y,s12-s10, -size.x));
    vec4 bump = vec4( cross(va,vb), 1 );

    imageStore(normals, uv, bump);
    imageStore(normals, uv, vec4(vec3(resultingDisplacement),1));


    vec4 albedoResult = vec4(diffuseColor,1);

    //https://arm-software.github.io/opengl-es-sdk-for-android/ocean_f_f_t.html
    #define LAMBDA 1.2
    float aX = textureLodOffset(displacementX, uvTextureSpace, 0.0, ivec2(+1, 0)).x;
    float aY = textureLodOffset(displacementY, uvTextureSpace, 0.0, ivec2(+1, 0)).x;
    float bX = textureLodOffset(displacementX, uvTextureSpace, 0.0, ivec2(-1, 0)).x;
    float bY = textureLodOffset(displacementY, uvTextureSpace, 0.0, ivec2(-1, 0)).x;

    float cX = textureLodOffset(displacementX, uvTextureSpace, 0.0, ivec2(0, 1)).x;
    float cY = textureLodOffset(displacementY, uvTextureSpace, 0.0, ivec2(0, 1)).x;
    float dX = textureLodOffset(displacementX, uvTextureSpace, 0.0, ivec2(0, -1)).x;
    float dY = textureLodOffset(displacementY, uvTextureSpace, 0.0, ivec2(0, -1)).x;

    vec2 dDdx = 0.5 * LAMBDA * (vec2(aX, aY) - vec2(bX, bY));
    vec2 dDdy = 0.5 * LAMBDA * (vec2(cX, cY) - vec2(dX, dY));
    const float scale = 15.0f;
    float j = jacobian(dDdx * scale, dDdy * scale);
    float resultingRoughness = clamp(1-j, 0f, 1f);

    float turbulence = 0.25f*max(2.0 - j + dot(abs(bump.xz), vec2(1.2)), 0.0);
    // This is rather "arbitrary", but looks pretty good in practice.
    //    vec3 color_mod = vec3(3.0 * smoothstep(1.2, 1.8, turbulence));
    vec3 color_mod = vec3(mix(diffuseColor.rgb*0.5, vec3(diffuseColor*1.1), turbulence));
    color_mod += 0.2f*vec3(resultingRoughness);

        imageStore(albedo, uv, vec4(color_mod, resultingRoughness));
        imageStore(roughness, uv, vec4(resultingRoughness));
//    imageStore(albedo, uv, vec4(diffuseColor, 1));
//    imageStore(roughness, uv, vec4(1));

    //    imageStore(albedo, uv, vec4(textureLod(debug, uvTextureSpace, 0).r));
//        imageStore(albedo, uv, vec4(uvTextureSpace, 0, 0));
}
