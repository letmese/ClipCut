package com.clipcut.core.effects

import com.clipcut.core.model.FilterType

/**
 * EffectDescriptor — the metadata for each GLSL effect entry.
 *
 *  id           — stable identifier stored on TimelineItem.effectId
 *  displayName  — shown in EffectsSheet grid
 *  fragmentShader — GLSL source. When TimelineItem.effectId is set, the
 *                  PipelineRenderer wraps the base ColorAdjustFilter output
 *                  with this fragment program after the FBO copy.
 *  defaultIntensity — slider initial value (0..1)
 *
 * 40+ effects registered following the spec.
 */
data class EffectDescriptor(
    val id: String,
    val displayName: String,
    val defaultIntensity: Float = 0.5f,
    val fragmentShader: String = DEFAULT_EFFECT_SHADER
)

object EffectRegistry {
    val all: List<EffectDescriptor> = listOf(
        EffectDescriptor("glitch", "Glitch", 0.6f, GLITCH_SHADER),
        EffectDescriptor("vhs", "VHS", 0.5f, VHS_SHADER),
        EffectDescriptor("rgb_split", "RGB Split", 0.4f, RGB_SPLIT_SHADER),
        EffectDescriptor("film_grain", "Film Grain", 0.3f, FILM_GRAIN_SHADER),
        EffectDescriptor("light_leak", "Light Leak", 0.4f, LIGHT_LEAK_SHADER),
        EffectDescriptor("bokeh", "Bokeh", 0.4f, BOKEH_SHADER),
        EffectDescriptor("kaleidoscope", "Kaleidoscope", 0.5f, KALEIDOSCOPE_SHADER),
        EffectDescriptor("zoom_pulse", "Zoom Pulse", 0.3f, ZOOM_PULSE_SHADER),
        EffectDescriptor("shake", "Shake", 0.5f, SHAKE_SHADER),
        EffectDescriptor("chroma_aberration", "Chromatic Aberration", 0.3f, RGB_SPLIT_SHADER),
        EffectDescriptor("bloom", "Bloom", 0.4f, BLOOM_SHADER),
        EffectDescriptor("halation", "Halation", 0.4f, HALATION_SHADER),
        EffectDescriptor("duotone", "Duotone", 0.6f, DUOTONE_SHADER),
        EffectDescriptor("posterize", "Posterize", 0.5f, POSTERIZE_SHADER),
        EffectDescriptor("pixelate", "Pixelate", 0.4f, PIXELATE_SHADER),
        EffectDescriptor("mosaic", "Mosaic", 0.5f, MOSAIC_SHADER),
        EffectDescriptor("scan_lines", "Scan Lines", 0.4f, SCAN_LINES_SHADER),
        EffectDescriptor("vignette_pulse", "Vignette Pulse", 0.5f, VIGNETTE_PULSE_SHADER),
        EffectDescriptor("wave", "Wave", 0.5f, WAVE_SHADER),
        EffectDescriptor("mirror", "Mirror", 0.5f, MIRROR_SHADER),
        EffectDescriptor("neon_glow", "Neon Glow", 0.5f, NEON_GLOW_SHADER),
        EffectDescriptor("comic", "Comic", 0.5f, COMIC_SHADER),
        EffectDescriptor("sketch", "Sketch", 0.5f, SKETCH_SHADER),
        EffectDescriptor("oil_paint", "Oil Paint", 0.4f, OIL_PAINT_SHADER),
        EffectDescriptor("watercolor", "Watercolor", 0.4f, WATERCOLOR_SHADER),
        EffectDescriptor("noise_static", "Noise Static", 0.3f, NOISE_SHADER),
        EffectDescriptor("edge_detect", "Edge Detect", 0.4f, EDGE_DETECT_SHADER),
        EffectDescriptor("emboss", "Emboss", 0.5f, EMBOSS_SHADER),
        EffectDescriptor("old_film", "Old Film", 0.6f, OLD_FILM_SHADER),
        EffectDescriptor("sharpen", "Sharpen", 0.3f, SHARPEN_SHADER),
        EffectDescriptor("blur_gaussian", "Gaussian Blur", 0.4f, BLUR_SHADER),
        EffectDescriptor("blur_motion", "Motion Blur", 0.4f, MOTION_BLUR_SHADER),
        EffectDescriptor("invert", "Invert", 1.0f, INVERT_SHADER),
        EffectDescriptor("thermal", "Thermal", 0.7f, THERMAL_SHADER),
        EffectDescriptor("night_vision", "Night Vision", 0.6f, NIGHT_VISION_SHADER),
        EffectDescriptor("ascii_art", "ASCII", 0.5f, ASCII_SHADER),
        EffectDescriptor("dot_screen", "Dot Screen", 0.4f, DOT_SCREEN_SHADER),
        EffectDescriptor("crosshatch", "Crosshatch", 0.4f, CROSSHATCH_SHADER),
        EffectDescriptor("diamond", "Diamond", 0.4f, DIAMOND_SHADER),
        EffectDescriptor("ripple", "Ripple", 0.5f, RIPPLE_SHADER),
        EffectDescriptor("shockwave", "Shockwave", 0.5f, SHOCKWAVE_SHADER)
    )

    fun byId(id: String): EffectDescriptor? = all.firstOrNull { it.id == id }
}

const val DEFAULT_EFFECT_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    gl_FragColor = c;
}
"""

const val GLITCH_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
float rand(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    float t = uTime;
    vec2 uv = vTexCoord;
    float line = step(0.96, rand(vec2(floor(uv.y * 100.0), floor(t))));
    uv.x += line * 0.05 * uIntensity * (rand(vec2(t)) - 0.5);
    vec4 r = texture2D(sTexture, uv + vec2(uIntensity * 0.02, 0.0));
    vec4 g = texture2D(sTexture, uv);
    vec4 b = texture2D(sTexture, uv - vec2(uIntensity * 0.02, 0.0));
    gl_FragColor = vec4(r.r, g.g, b.b, 1.0);
}
"""

const val VHS_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec2 uv = vTexCoord;
    float scan = sin(uv.y * 220.0 + uTime * 5.0) * 0.04 * uIntensity;
    vec4 c = texture2D(sTexture, uv + vec2(scan, 0.0));
    c.rgb -= sin(uv.y * 100.0) * 0.05 * uIntensity;
    gl_FragColor = vec4(c.rgb, 1.0);
}
"""

const val RGB_SPLIT_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    float d = uIntensity * 0.02;
    float r = texture2D(sTexture, vTexCoord + vec2(d, 0.0)).r;
    float g = texture2D(sTexture, vTexCoord).g;
    float b = texture2D(sTexture, vTexCoord - vec2(d, 0.0)).b;
    gl_FragColor = vec4(r, g, b, 1.0);
}
"""

const val FILM_GRAIN_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
float rand(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float n = rand(vTexCoord + vec2(uTime)) * uIntensity;
    gl_FragColor = vec4(c.rgb + n * 0.3, 1.0);
}
"""

const val LIGHT_LEAK_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float leak = smoothstep(0.4, 1.0, vTexCoord.x + vTexCoord.y * 0.5);
    c.rgb += vec3(0.9, 0.5, 0.2) * leak * uIntensity;
    gl_FragColor = c;
}
"""

const val BOKEH_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec2 p = vTexCoord - 0.5;
    c.rgb *= smoothstep(0.0, 0.5 * uIntensity, length(p));
    gl_FragColor = c;
}
"""

const val KALEIDOSCOPE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec2 p = vTexCoord - 0.5;
    float r = length(p);
    float a = atan(p.y, p.x);
    a = mod(a, 0.785) * 8.0;
    vec2 uv = vec2(cos(a), sin(a)) * r + 0.5;
    gl_FragColor = mix(texture2D(sTexture, vTexCoord), texture2D(sTexture, uv), uIntensity);
}
"""

const val ZOOM_PULSE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    float z = 1.0 + sin(uTime * 3.0) * 0.1 * uIntensity;
    vec2 uv = (vTexCoord - 0.5) / z + 0.5;
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val SHAKE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec2 uv = vTexCoord + vec2(sin(uTime * 30.0), cos(uTime * 27.0)) * 0.01 * uIntensity;
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val BLOOM_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec3 b = vec3(0.0);
    for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
        vec2 off = vec2(float(i), float(j)) * 0.005;
        b += texture2D(sTexture, vTexCoord + off).rgb;
    }
    b /= 25.0;
    gl_FragColor = vec4(c.rgb + max(b - 0.6, 0.0) * uIntensity, 1.0);
}
"""

const val HALATION_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float hot = max(c.r, max(c.g, c.b));
    vec3 glow = vec3(1.0, 0.7, 0.4) * pow(hot, 3.0) * uIntensity;
    gl_FragColor = vec4(c.rgb + glow, 1.0);
}
"""

const val DUOTONE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 a = vec3(0.1, 0.05, 0.2);
    vec3 b = vec3(0.9, 0.5, 0.2);
    vec3 duo = mix(a, b, g);
    gl_FragColor = vec4(mix(c.rgb, duo, uIntensity), 1.0);
}
"""

const val POSTERIZE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float levels = mix(2.0, 8.0, 1.0 - uIntensity);
    c.rgb = floor(c.rgb * levels) / levels;
    gl_FragColor = c;
}
"""

const val PIXELATE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    float size = mix(512.0, 32.0, uIntensity);
    vec2 uv = floor(vTexCoord * size) / size;
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val MOSAIC_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    float size = mix(256.0, 16.0, uIntensity);
    vec2 uv = floor(vTexCoord * size) / size;
    vec4 c = texture2D(sTexture, uv);
    float dx = fract(vTexCoord.x * size) - 0.5;
    float dy = fract(vTexCoord.y * size) - 0.5;
    float dot = 1.0 - sqrt(dx*dx + dy*dy) * 2.0;
    gl_FragColor = vec4(c.rgb * max(dot, 0.0), 1.0);
}
"""

const val SCAN_LINES_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float s = 0.5 + 0.5 * sin(vTexCoord.y * 600.0);
    c.rgb *= 1.0 - (1.0 - s) * uIntensity;
    gl_FragColor = c;
}
"""

const val VIGNETTE_PULSE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float d = distance(vTexCoord, vec2(0.5));
    float pulse = 0.7 + 0.3 * sin(uTime * 2.0);
    c.rgb *= 1.0 - smoothstep(0.3, 0.7, d * pulse) * uIntensity;
    gl_FragColor = c;
}
"""

const val WAVE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec2 uv = vTexCoord;
    uv.y += sin(uv.x * 30.0 + uTime * 3.0) * 0.02 * uIntensity;
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val MIRROR_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec2 uv = vec2(mix(vTexCoord.x, 1.0 - vTexCoord.x, step(0.5, vTexCoord.x) * uIntensity), vTexCoord.y);
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val NEON_GLOW_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec3 b = vec3(0.0);
    for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
        b += texture2D(sTexture, vTexCoord + vec2(float(i), float(j)) * 0.003).rgb;
    }
    b /= 9.0;
    vec3 neon = vec3(0.3, 0.9, 1.0) * length(b - c.rgb) * 4.0;
    gl_FragColor = vec4(c.rgb + neon * uIntensity, 1.0);
}
"""

const val COMIC_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float q = floor(g * 4.0) / 4.0;
    c.rgb = mix(c.rgb, vec3(q), uIntensity);
    gl_FragColor = c;
}
"""

const val SKETCH_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec4 l = texture2D(sTexture, vTexCoord - vec2(0.002, 0.0));
    vec4 r = texture2D(sTexture, vTexCoord + vec2(0.002, 0.0));
    vec4 u = texture2D(sTexture, vTexCoord - vec2(0.0, 0.002));
    vec4 d = texture2D(sTexture, vTexCoord + vec2(0.0, 0.002));
    float edge = length((l - r).rgb) + length((u - d).rgb);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float s = clamp(1.0 - edge * 6.0, 0.0, 1.0) * g;
    gl_FragColor = vec4(mix(c.rgb, vec3(s), uIntensity), 1.0);
}
"""

const val OIL_PAINT_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec4 sum = vec4(0.0);
    for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
        sum += texture2D(sTexture, vTexCoord + vec2(float(i), float(j)) * 0.003);
    }
    sum /= 25.0;
    gl_FragColor = mix(c, sum, uIntensity);
}
"""

const val WATERCOLOR_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec4 blur = vec4(0.0);
    for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) {
        blur += texture2D(sTexture, vTexCoord + vec2(float(i), float(j)) * 0.004);
    }
    blur /= 49.0;
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 wc = mix(c.rgb, blur.rgb, 0.5) * (0.5 + 0.5 * g);
    gl_FragColor = vec4(mix(c.rgb, wc, uIntensity), 1.0);
}
"""

const val NOISE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
float rand(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    c.rgb = mix(c.rgb, vec3(rand(vTexCoord + uTime)), uIntensity);
    gl_FragColor = c;
}
"""

const val EDGE_DETECT_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 l = texture2D(sTexture, vTexCoord - vec2(0.002, 0.0));
    vec4 r = texture2D(sTexture, vTexCoord + vec2(0.002, 0.0));
    vec4 u = texture2D(sTexture, vTexCoord - vec2(0.0, 0.002));
    vec4 d = texture2D(sTexture, vTexCoord + vec2(0.0, 0.002));
    float edge = length((l - r).rgb) + length((u - d).rgb);
    gl_FragColor = vec4(vec3(edge * 4.0), 1.0) * uIntensity + texture2D(sTexture, vTexCoord) * (1.0 - uIntensity);
}
"""

const val EMBOSS_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 a = texture2D(sTexture, vTexCoord - vec2(0.002));
    vec4 b = texture2D(sTexture, vTexCoord + vec2(0.002));
    float g = (b.r - a.r) + (b.g - a.g) + (b.b - a.b);
    gl_FragColor = vec4(vec3(g + 0.5), 1.0) * uIntensity + texture2D(sTexture, vTexCoord) * (1.0 - uIntensity);
}
"""

const val OLD_FILM_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
float rand(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float n = rand(vTexCoord + uTime) * 0.1 * uIntensity;
    float flicker = 0.95 + 0.05 * sin(uTime * 12.0);
    gl_FragColor = vec4(vec3(g) * flicker + n, 1.0);
}
"""

const val SHARPEN_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    vec4 n = texture2D(sTexture, vTexCoord + vec2(0.001));
    gl_FragColor = c + (c - n) * uIntensity * 3.0;
}
"""

const val BLUR_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 sum = vec4(0.0);
    float r = uIntensity * 0.01;
    for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
        sum += texture2D(sTexture, vTexCoord + vec2(float(i), float(j)) * r);
    }
    gl_FragColor = sum / 25.0;
}
"""

const val MOTION_BLUR_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 sum = vec4(0.0);
    for (int i = 0; i < 8; i++) {
        float t = float(i) / 8.0;
        sum += texture2D(sTexture, vTexCoord + vec2(t * uIntensity * 0.02, 0.0));
    }
    gl_FragColor = sum / 8.0;
}
"""

const val INVERT_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    gl_FragColor = vec4(mix(c.rgb, 1.0 - c.rgb, uIntensity), 1.0);
}
"""

const val THERMAL_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float t = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 thermal = mix(vec3(0.0, 0.0, 0.5), vec3(1.0, 0.5, 0.0), t);
    thermal = mix(thermal, vec3(1.0, 1.0, 0.0), smoothstep(0.6, 1.0, t));
    gl_FragColor = vec4(mix(c.rgb, thermal, uIntensity), 1.0);
}
"""

const val NIGHT_VISION_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float vign = 1.0 - distance(vTexCoord, vec2(0.5)) * 0.5;
    vec3 nv = vec3(0.2, 1.0, 0.2) * g * vign;
    nv += vec3(0.0, 0.05, 0.0) * sin(uTime * 30.0);
    gl_FragColor = vec4(mix(c.rgb, nv, uIntensity), 1.0);
}
"""

const val ASCII_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float block = floor(g * 8.0) / 8.0;
    gl_FragColor = vec4(vec3(block), 1.0);
}
"""

const val DOT_SCREEN_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec2 p = fract(vTexCoord * 200.0) - 0.5;
    float dot = 1.0 - smoothstep(0.1, 0.5, length(p)) * 0.5;
    gl_FragColor = vec4(vec3(g * dot), 1.0);
}
"""

const val CROSSHATCH_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(sTexture, vTexCoord);
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    float h = step(0.5, abs(fract(vTexCoord.x * 100.0) - 0.5));
    float v = step(0.5, abs(fract(vTexCoord.y * 100.0) - 0.5));
    float ink = 1.0 - max(h, v) * 0.5;
    gl_FragColor = vec4(vec3(g * ink), 1.0);
}
"""

const val DIAMOND_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
void main() {
    vec2 p = abs(vTexCoord - 0.5);
    float d = p.x + p.y;
    vec4 c = texture2D(sTexture, vTexCoord);
    gl_FragColor = vec4(c.rgb * smoothstep(0.5, 0.4, d * uIntensity + 0.1), 1.0);
}
"""

const val RIPPLE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec2 p = vTexCoord - 0.5;
    float r = length(p);
    vec2 uv = vTexCoord + vec2(sin(r * 50.0 - uTime * 5.0) * 0.02 * uIntensity);
    gl_FragColor = texture2D(sTexture, uv);
}
"""

const val SHOCKWAVE_SHADER = """
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D sTexture;
uniform float uIntensity;
uniform float uTime;
void main() {
    vec2 p = vTexCoord - 0.5;
    float r = length(p);
    float wave = sin((r - uTime * 0.3) * 30.0) * 0.02 * uIntensity;
    vec2 uv = vTexCoord + normalize(p) * wave;
    gl_FragColor = texture2D(sTexture, uv);
}
"""
