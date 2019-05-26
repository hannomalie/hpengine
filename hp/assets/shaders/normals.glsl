
mat3 cotangent_frame( vec3 N, vec3 p, vec2 uv )
{
    vec3 dp1 = dFdx( p );
    vec3 dp2 = dFdy( p );
    vec2 duv1 = dFdx( uv );
    vec2 duv2 = dFdy( uv );

    vec3 dp2perp = cross( dp2, N );
    vec3 dp1perp = cross( N, dp1 );
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;

    float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
    return mat3( T * invmax, B * invmax, N );
}
vec3 perturb_normal(vec3 N, vec3 V, vec2 texcoord, sampler2D normalMap)
{
    vec3 map = (texture(normalMap, texcoord)).xyz;
    map = map * 2 - 1;
    mat3 TBN = cotangent_frame( N, V, texcoord );
    return normalize( TBN * map );
}
vec2 parallaxMapping(sampler2D heightMap, vec2 texCoords, vec3 viewDir, float height_scale, float parallaxBias)
{
    float height =  texture(heightMap, texCoords).r;
    if(height < parallaxBias) {
        height = 0;
    };
    vec2 p = viewDir.xy / viewDir.z * (height * height_scale);
    return texCoords - p;
}