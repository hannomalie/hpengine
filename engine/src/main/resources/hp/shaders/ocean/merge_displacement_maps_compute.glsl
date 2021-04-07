layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0, rgba32f) uniform writeonly image2D displacement;
layout (binding = 1) uniform sampler2D displacementX;
layout (binding = 2) uniform sampler2D displacementY;
layout (binding = 3) uniform sampler2D displacementZ;
layout (binding = 4, rgba32f) uniform writeonly image2D normals;
layout (binding = 5, rgba32f) uniform writeonly image2D albedo;
layout (binding = 6, rgba32f) uniform writeonly image2D roughness;
layout (binding = 7) uniform sampler2D debug;

uniform int N = 256;
uniform mat4 viewMatrix;
uniform vec3 diffuseColor = vec3(0.05,0.2,0.8);

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
    vec2 uvTextureSpace = vec2(uv)/float(N);
    float x = textureLod(displacementX, uvTextureSpace, 0).x;
    float y = textureLod(displacementY, uvTextureSpace, 0).x;
    float z = textureLod(displacementZ, uvTextureSpace, 0).x;
    imageStore(displacement, uv, vec4(x, y, z, 1));

    float s01 = 100*textureLod(displacementY, vec2(uv + off.xy)/float(N), 0).x;
    float s21 = 100*textureLod(displacementY, vec2(uv + off.zy)/float(N), 0).x;
    float s10 = 100*textureLod(displacementY, vec2(uv + off.yx)/float(N), 0).x;
    float s12 = 100*textureLod(displacementY, vec2(uv + off.yz)/float(N), 0).x;
    vec3 va = normalize(vec3(size.x,s21-s01, size.y));
    vec3 vb = normalize(vec3(size.y,s12-s10, -size.x));
    vec4 bump = vec4( cross(va,vb), 1 );

    imageStore(normals, uv, bump);
    vec4 albedoResult = vec4(diffuseColor,1);

    //https://arm-software.github.io/opengl-es-sdk-for-android/ocean_f_f_t.html
    #define LAMBDA 1.2
    const float scale = 50.0f;
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
    float j = jacobian(dDdx * scale, dDdy * scale);

    float turbulence = max(2.0 - j + dot(abs(bump.xz), vec2(1.2)), 0.0);
    // This is rather "arbitrary", but looks pretty good in practice.
    float color_mod = 1.0 + 3.0 * smoothstep(1.2, 1.8, turbulence);

    //    imageStore(albedo, uv, 0.25*(albedoResult * vec4(color_mod,color_mod,color_mod,0)));
    //    imageStore(roughness, uv, vec4(1-j));
    imageStore(albedo, uv, vec4(diffuseColor,0));
    imageStore(roughness, uv, vec4(1));

    //    imageStore(albedo, uv, vec4(textureLod(debug, uvTextureSpace, 0).r));
    //    imageStore(albedo, uv, vec4(uvTextureSpace, 0, 0));
}
