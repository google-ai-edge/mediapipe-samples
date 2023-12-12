
#include <metal_stdlib>
using namespace metal;

half4 choseColor(half4 color1, half4 color2, float cf) {
  if (cf > 0.5) {
    return color1;
  } else {
    return half4(color2 * (1 - cf) + color1 * cf);
  }
}

kernel void drawWithInvertedColor(texture2d<half, access::read> inTexture [[ texture (0) ]],
                                  texture2d<half, access::read> inTexture2 [[ texture (1) ]],
                                  texture2d<half, access::read_write> outTexture [[ texture (2) ]],
                                  device float* data_in [[ buffer(0) ]],
                                  uint2 gid [[ thread_position_in_grid ]]) {
  half4 color1 = inTexture.read(gid).rgba;
  half4 color2 = inTexture2.read(gid).rgba;
  float cf = data_in[gid.y * outTexture.get_width() + gid.x];
  half4 out = choseColor(color1, color2, cf);
  outTexture.write(out, gid);
}
