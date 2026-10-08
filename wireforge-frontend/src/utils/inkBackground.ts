export interface InkBackground {
  resize(width: number, height: number): void
  render(seconds: number, dark: boolean): void
  dispose(): void
}

const vertexSource = `
attribute vec2 a_position;
void main() {
  gl_Position = vec4(a_position, 0.0, 1.0);
}`

const fragmentSource = `
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_dark;

float hash(vec2 p) {
  vec3 q = fract(vec3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
  vec2 cell = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(cell), hash(cell + vec2(1.0, 0.0)), f.x),
    mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0, 1.0)), f.x),
    f.y
  );
}

float cloud(vec2 p) {
  float value = 0.0;
  float strength = 0.57;
  mat2 turn = mat2(0.80, -0.60, 0.60, 0.80);
  for (int i = 0; i < 3; i++) {
    value += strength * noise(p);
    p = turn * p * 2.02 + vec2(3.1, 1.7);
    strength *= 0.48;
  }
  return value;
}

void main() {
  vec2 uv = gl_FragCoord.xy / u_resolution;
  vec2 p = (uv - 0.5) * vec2(u_resolution.x / u_resolution.y, 1.0) * 2.4;
  float t = u_time * 0.24;

  // An evolving flow field bends the pigment boundaries, rather than moving a fixed image.
  vec2 current = vec2(
    cloud(p * 0.85 + vec2(t * 0.55, -t * 0.32)),
    cloud(p * 0.85 + vec2(5.2 - t * 0.40, 3.7 + t * 0.48))
  );
  vec2 flow = p + (current - 0.5) * 2.4;
  flow += 0.35 * vec2(
    sin(flow.y * 2.0 + t),
    cos(flow.x * 1.8 - t * 0.9)
  );

  // Independent pigment fields continually replace and mix with each other at every position.
  vec4 pigment = vec4(
    cloud(flow * 1.05 + vec2(t * 0.65, 1.8)),
    cloud(flow * 1.00 + vec2(7.4, -t * 0.58)),
    cloud(flow * 1.08 + vec2(3.6 - t * 0.54, 8.1)),
    cloud(flow * 0.96 + vec2(12.3, 4.2 + t * 0.62))
  );
  vec4 weight = exp((pigment - 0.5) * 11.0);
  weight /= dot(weight, vec4(1.0));

  vec3 cyan = vec3(0.20, 0.78, 0.92);
  vec3 blue = vec3(0.34, 0.56, 0.96);
  vec3 violet = vec3(0.68, 0.34, 0.90);
  vec3 green = vec3(0.53, 0.82, 0.34);
  vec3 dye = cyan * weight.x + blue * weight.y + violet * weight.z + green * weight.w;

  float density = smoothstep(0.28, 0.70, dot(pigment, weight));
  float alpha = mix(0.18, 0.42, density) * mix(1.0, 1.12, u_dark);
  gl_FragColor = vec4(dye, alpha);
}`

/** A decorative, resolution-limited pigment field. No network or application state is involved. */
export function createInkBackground(canvas: HTMLCanvasElement): InkBackground | null {
  let context: WebGLRenderingContext | null
  try {
    context = canvas.getContext('webgl', {
      alpha: true,
      premultipliedAlpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      powerPreference: 'low-power',
    })
  } catch {
    return null
  }
  if (!context) return null
  const gl = context

  const shaders: WebGLShader[] = []
  let program: WebGLProgram | null = null
  let buffer: WebGLBuffer | null = null
  const dispose = () => {
    if (buffer) gl.deleteBuffer(buffer)
    if (program) gl.deleteProgram(program)
    shaders.forEach(shader => gl.deleteShader(shader))
    buffer = null
    program = null
  }

  try {
    const compile = (type: number, source: string) => {
      const shader = gl.createShader(type)
      if (!shader) throw new Error('Unable to create background shader')
      shaders.push(shader)
      gl.shaderSource(shader, source)
      gl.compileShader(shader)
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error('Background shader unavailable')
      return shader
    }
    const precision = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT)?.precision ? 'highp' : 'mediump'
    program = gl.createProgram()
    if (!program) throw new Error('Unable to create background program')
    gl.attachShader(program, compile(gl.VERTEX_SHADER, vertexSource))
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, `precision ${precision} float;\n${fragmentSource}`))
    gl.linkProgram(program)
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Background program unavailable')

    buffer = gl.createBuffer()
    if (!buffer) throw new Error('Unable to create background buffer')
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
    gl.useProgram(program)
    const position = gl.getAttribLocation(program, 'a_position')
    gl.enableVertexAttribArray(position)
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)
    const resolution = gl.getUniformLocation(program, 'u_resolution')
    const time = gl.getUniformLocation(program, 'u_time')
    const dark = gl.getUniformLocation(program, 'u_dark')

    return {
      resize(width, height) {
        // The soft background needs neither device-pixel scaling nor a full-size GPU buffer.
        const scale = Math.min(1, 900 / Math.max(width, 1), Math.sqrt(360000 / Math.max(width * height, 1)))
        const w = Math.max(1, Math.round(width * scale))
        const h = Math.max(1, Math.round(height * scale))
        if (canvas.width !== w || canvas.height !== h) {
          canvas.width = w
          canvas.height = h
        }
        gl.viewport(0, 0, w, h)
        gl.uniform2f(resolution, w, h)
      },
      render(seconds, isDark) {
        if (!program || gl.isContextLost()) return
        gl.uniform1f(time, seconds)
        gl.uniform1f(dark, isDark ? 1 : 0)
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      },
      dispose,
    }
  } catch {
    dispose()
    return null
  }
}
