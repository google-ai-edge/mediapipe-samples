
const createShaderProgram = (gl) => {
  const vs = `
    attribute vec2 position;
    varying vec2 texCoords;
  
    void main() {
      texCoords = (position + 1.0) / 2.0;
      texCoords.y = 1.0 - texCoords.y;
      gl_Position = vec4(position, 0, 1.0);
    }
  `

  const fs = `
    precision highp float;
    varying vec2 texCoords;
    uniform sampler2D textureSampler;
    void main() {
      float a = texture2D(textureSampler, texCoords).r;
      gl_FragColor = vec4(a,a,a,a);
    }
  `
  const vertexShader = gl.createShader(gl.VERTEX_SHADER)
  if (!vertexShader) {
    throw Error('can not create vertex shader')
  }
  gl.shaderSource(vertexShader, vs)
  gl.compileShader(vertexShader)

  // Create our fragment shader
  const fragmentShader = gl.createShader(gl.FRAGMENT_SHADER)
  if (!fragmentShader) {
    throw Error('can not create fragment shader')
  }
  gl.shaderSource(fragmentShader, fs)
  gl.compileShader(fragmentShader)

  // Create our program
  const program = gl.createProgram()
  if (!program) {
    throw Error('can not create program')
  }
  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)

  return {
    vertexShader,
    fragmentShader,
    shaderProgram: program,
    attribLocations: {
      position: gl.getAttribLocation(program, 'position')
    },
    uniformLocations: {
      textureSampler: gl.getUniformLocation(program, 'textureSampler')
    }
  }
}
const createVertexBuffer = (gl) => {
  if (!gl) {
    return null
  }
  const vertexBuffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer)
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array([-1, -1, -1, 1, 1, 1, -1, -1, 1, 1, 1, -1]),
    gl.STATIC_DRAW
  )
  return vertexBuffer
}

export function createCopyTextureToCanvas(
  canvas
) {
  const gl = canvas.getContext('webgl2')
  if (!gl) {
    return undefined
  }
  const {
    shaderProgram,
    attribLocations: { position: positionLocation },
    uniformLocations: { textureSampler: textureLocation }
  } = createShaderProgram(gl)
  const vertexBuffer = createVertexBuffer(gl)

  return (mask) => {
    gl.viewport(0, 0, canvas.width, canvas.height)
    gl.clearColor(1.0, 1.0, 1.0, 1.0)
    gl.useProgram(shaderProgram)
    gl.clear(gl.COLOR_BUFFER_BIT)
    const texture = mask.getAsWebGLTexture()
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer)
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0)
    gl.enableVertexAttribArray(positionLocation)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, texture)
    gl.uniform1i(textureLocation, 0)
    gl.drawArrays(gl.TRIANGLES, 0, 6)
    return createImageBitmap(canvas)
  }
}

