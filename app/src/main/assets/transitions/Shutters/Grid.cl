#define vec2 float2
#define vec3 float3
#define vec4 float4
#define rgb xyz
#define rgba xyzw

const sampler_t sampler = CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

vec4 INPUT(image2d_t src_data, __global FilterParam* param, vec2 tc)
{
	tc = (vec2)(tc.x, tc.y)*(vec2)(param->origROI[2], param->origROI[3]) + (vec2)(param->origROI[0], param->origROI[1]);
	return read_imagef(src_data, sampler, (vec2)(tc.x, 1.0f - tc.y));
}

float normalizedRandom(vec2 co)
{
	//float input1 = n;
	float temp; 
    return fract(sin(dot(co.xy ,(vec2)(12.9898,78.233f))) * 43758.5453f, &temp);
}

float rand (vec2 co) {
    return normalizedRandom(sin(dot(co.xy ,(vec2)(12.9898,78.233f))) * 43758.5453f);
}

float getDelta(vec2 p) {
    vec2 rectanglePos = floor(p * 4.0f);
    vec2 rectangleSize = (vec2)(1.0f / 4.0f, 1.0f / 4.0f);
    float top = rectangleSize.y * (rectanglePos.y + 1.0f);
    float bottom = rectangleSize.y * rectanglePos.y;
    float left = rectangleSize.x * rectanglePos.x;
    float right = rectangleSize.x * (rectanglePos.x + 1.0f);
    float minX = min(fabs(p.x - left), fabs(p.x - right));
    float minY = min(fabs(p.y - top), fabs(p.y - bottom));
    return min(minX, minY);
}

float getDividerSize() {
	float dividerWidth = 0.05f;
    vec2 rectangleSize = (vec2)(1.0f / 4.0f, 1.0f / 4.0f);
    return min(rectangleSize.x, rectangleSize.y) * dividerWidth;
}

static vec2 scale(vec2 uv, float amp)
{
	return fmod((uv + 0.5f)*(amp), 1.0f);
}

static vec2 myMod(vec2 x, float y)
{
	return x-y * floor(x/y);
}

__kernel void MAIN(__read_only image2d_t input1, __read_only image2d_t input2, __write_only image2d_t dstImg,__global FilterParam* param)
{
    float progress = param->cur_time / param->total_time;
	int W = get_global_size(0);
	int H = get_global_size(1);
	int textH = param->height[2];
	int w = get_global_id(0);
	int h = get_global_id(1);
	float2 resolution = (float2)(W,H);
	int2 gl_FragCoord = (int2)(get_global_id(0), get_global_id(1));
	vec2 fragCoord = (vec2)(get_global_id0( param), get_global_id1( param));
	vec2 uv = fragCoord/resolution.xy;
	vec4 outputCol = (vec4)(1.0f);
	
	vec2 p = uv;
	float pause = 0.1f;
	vec4 bgcolor = (vec4)(0.0f, 0.0f, 0.0f, 1.0f);
	
	float randomness = 0.1f;
	vec4 y1 = INPUT(input1, param, p);
	vec4 y2 = INPUT(input2, param, p);
	
	//vec4 bgcolor =y2;
    if(progress < pause) 
    {
		float currentProg = progress / pause;
		float a = 1.0f;
        if(getDelta(p) < getDividerSize()) 
        {
            a = 1.0f - currentProg;
        }
        //return mix(bgcolor, y1, a);
	    outputCol = mix(bgcolor, y1, a);
    }
    else if(progress < 1.0f - pause)
    {
        if(getDelta(p) < getDividerSize()) 
        {
            //return bgcolor;
            outputCol = bgcolor;
        } 
        else 
        {
            float currentProg = (progress - pause) / (1.0f - pause * 2.0f);
            vec2 q = p;
            vec2 rectanglePos = floor(q * 4.0f);
        
            float r = rand(rectanglePos) - randomness;
            float cp = smoothstep(0.0f, 1.0f - r, currentProg);
        
            float rectangleSize = 1.0f / 4.0f;
            float delta = rectanglePos.x * rectangleSize;
            float offset = rectangleSize / 2.0f + delta;
        
            p.x = (p.x - offset)/fabs(cp - 0.5f)*0.5f + offset;
        
            float s = step(fabs(4.0f * (q.x - delta) - 0.5f), fabs(cp - 0.5f));
            //return mix(bgcolor, mix(y2, y1, step(cp, 0.5f)), s);
            outputCol = mix(bgcolor, mix(y2, y1, step(cp, 0.5f)), s);
        }
    }
    else 
    {
	    float currentProg = 1.0f-progress;
        float a = 1.0f;
        if(getDelta(p) < getDividerSize()) {
            a = currentProg;
        }
	    outputCol = mix(bgcolor, y2, a);
    }
	write_imagef(dstImg, (int2)(w, textH - h -1), outputCol);  
}