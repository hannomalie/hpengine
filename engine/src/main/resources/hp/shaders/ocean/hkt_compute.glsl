#define M_PI 3.1415926535897932384626433832795

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0, rgba32f) writeonly uniform image2D tilde_hkt_dy; //height displacement
layout (binding = 1, rgba32f) writeonly uniform image2D tilde_hkt_dx; //choppy-x displacement
layout (binding = 2, rgba32f) writeonly uniform image2D tilde_hkt_dz; //choppy-z displacement
layout (binding = 3, rgba32f) readonly uniform image2D tilde_h0k;
layout (binding = 4, rgba32f) readonly uniform image2D tilde_h0minusk;

uniform int N = 256;
uniform int L = 1000;
uniform float t;

struct complex
{
    float real;
    float im;
};

complex mul(complex c0, complex c1)
{
    complex c;
    c.real = c0.real * c1.real - c0.im * c1.im;
    c.im   = c0.real * c1.im + c0.im * c1.real;
    return c;
}

complex add(complex c0, complex c1)
{
    complex c;
    c.real = c0.real + c1.real;
    c.im   = c0.im   + c1.im;
    return c;
}

complex conj(complex c)
{
    complex c_conj = complex(c.real, -c.im);

    return c_conj;
}
vec2 c_exp(float x){
    return vec2(cos(x), sin(x));
}
vec2 c_conj(vec2 a){
    return vec2(a.x, -a.y);
}
//Complex arithmetic.
vec2 c_mul(vec2 a, vec2 b){
    return vec2(a.x * b.x - a.y * b.y, a.y * b.x + a.x * b.y);
}
void main(void)
{
//    vec2 x = ivec2(gl_GlobalInvocationID.xy);
    ivec2 x = ivec2(gl_GlobalInvocationID.xy);

    vec2 k = 2.0 * M_PI * x/vec2(L);

    float magnitude = length(k);
    float epsilon = 0.00001;
    if (magnitude < epsilon) magnitude = epsilon;

    float w = sqrt(9.81 * magnitude);

    complex fourier_amp = complex(
        imageLoad(tilde_h0k, ivec2(gl_GlobalInvocationID.xy)).r,
        imageLoad(tilde_h0k, ivec2(gl_GlobalInvocationID.xy)).g
    );

    complex fourier_amp_conj   = conj(complex(
        imageLoad(tilde_h0minusk, ivec2(gl_GlobalInvocationID.xy)).r,
        imageLoad(tilde_h0minusk, ivec2(gl_GlobalInvocationID.xy)).g
    ));

    float cosinus = cos(w*t);
    float sinus   = sin(w*t);

    // euler formula
    complex exp_iwt = complex(cosinus, sinus);
    complex exp_iwt_inv = complex(cosinus, -sinus);

    // dy
    complex h_k_t_dy = add(mul(fourier_amp, exp_iwt), mul(fourier_amp_conj, exp_iwt_inv));

    // dx
    complex dx = complex(0.0,-k.x/magnitude);
    complex h_k_t_dx = mul(dx, h_k_t_dy);

    // dz
    complex dy = complex(0.0,-k.y/magnitude);
    complex h_k_t_dz = mul(dy, h_k_t_dy);

    imageStore(tilde_hkt_dx, ivec2(gl_GlobalInvocationID.xy), vec4(h_k_t_dx.real, h_k_t_dx.im, 0, 1));
    imageStore(tilde_hkt_dy, ivec2(gl_GlobalInvocationID.xy), vec4(h_k_t_dy.real, h_k_t_dy.im, 0, 1));
    imageStore(tilde_hkt_dz, ivec2(gl_GlobalInvocationID.xy), vec4(h_k_t_dz.real, h_k_t_dz.im, 0, 1));

    int SIZE = N;
    int SIDE = L;
//    k = (2.0 * M_PI * ((x-1.5) - SIZE/2.0)) / SIDE;
    float dispersion = sqrt(9.81 * length(k)) * t;
    vec2 h0k = imageLoad(tilde_h0k, ivec2(gl_GlobalInvocationID.xy)).xy;
    vec2 h0minusk = imageLoad(tilde_h0minusk, ivec2(gl_GlobalInvocationID.xy)).xy;
    vec2 hkt = c_mul(h0k, c_exp(dispersion)) + c_conj(c_mul(h0minusk, c_exp(-dispersion)));
//    imageStore(tilde_hkt_dx, ivec2(gl_GlobalInvocationID.xy), vec4(0, 0, 0, 1));
//    imageStore(tilde_hkt_dy, ivec2(gl_GlobalInvocationID.xy), vec4(hkt, 0, 1));
//    imageStore(tilde_hkt_dz, ivec2(gl_GlobalInvocationID.xy), vec4(0, 0, 0, 1));
}
