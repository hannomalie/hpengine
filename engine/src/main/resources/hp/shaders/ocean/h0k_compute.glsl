#define M_PI 3.1415926535897932384626433832795

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0, rgba32f) writeonly uniform image2D tilde_h0k;
layout (binding = 1, rgba32f) writeonly uniform image2D tilde_h0minusk;
layout (binding = 2) uniform sampler2D random0;
layout (binding = 3) uniform sampler2D random1;
layout (binding = 4) uniform sampler2D random2;
layout (binding = 5) uniform sampler2D random3;

uniform	int N = 256;
uniform int L = 1000;
uniform float amplitude = 2;
uniform float windspeed = 26;
uniform vec2 direction = vec2(1.0f, 1.0f);

const float g = 9.81;

// Box-Muller-Method

vec4 gaussRND()
{
    vec2 texCoord = vec2(gl_GlobalInvocationID.xy)/float(N);

    float noise00 = clamp(textureLod(random0, texCoord, 0).r, 0.00001, 1.0);
    float noise01 = clamp(textureLod(random1, texCoord, 0).r, 0.00001, 1.0);
    float noise02 = clamp(textureLod(random2, texCoord, 0).r, 0.00001, 1.0);
    float noise03 = clamp(textureLod(random3, texCoord, 0).r, 0.00001, 1.0);

    float u0 = 2.0*M_PI*noise00;
    float v0 = sqrt(-2.0 * log(noise01));
    float u1 = 2.0*M_PI*noise02;
    float v1 = sqrt(-2.0 * log(noise03));

    vec4 rnd = vec4(v0 * cos(u0), v0 * sin(u0), v1 * cos(u1), v1 * sin(u1));

    return rnd;
}

void main(void)
{
    vec2 x = vec2(gl_GlobalInvocationID.xy);

    vec2 k = vec2(2.0 * M_PI * x.x/L, 2.0 * M_PI * x.y/L);

    float l = L / 2000.0;

    float L_ = (windspeed * windspeed)/g;
    float magnitude = length(k);
    if (magnitude < 0.0001) magnitude = 0.0001;
    float magnitudeSq = magnitude * magnitude;

    //sqrt(Ph(k))/sqrt(2)
    float h0k = sqrt(
        (amplitude/(magnitudeSq*magnitudeSq)) * pow(dot(normalize(k), normalize(direction)), 4) * exp(-(1.0/(magnitudeSq * L_ * L_))) * exp(-magnitudeSq*pow(l,2.0))
    ) / sqrt(2.0);
    h0k = clamp(h0k, 0.0, 10000.0);

    //sqrt(Ph(-k))/sqrt(2)
    float h0minusk = sqrt(
        (amplitude/(magnitudeSq*magnitudeSq)) * pow(dot(normalize(-k), normalize(direction)), 4) * exp(-(1.0/(magnitudeSq * L_ * L_))) * exp(-magnitudeSq*pow(l,2.0))
    ) / sqrt(2.0);
    h0minusk = clamp(h0minusk, 0.0, 10000.0);

    vec4 gauss_random = gaussRND();

    imageStore(tilde_h0k, ivec2(gl_GlobalInvocationID.xy), vec4(gauss_random.xy * h0k, 0, 1));

    imageStore(tilde_h0minusk, ivec2(gl_GlobalInvocationID.xy), vec4(gauss_random.zw * h0minusk, 0, 1));

//    imageStore(tilde_h0k, ivec2(gl_GlobalInvocationID.xy), gauss_random);
}
