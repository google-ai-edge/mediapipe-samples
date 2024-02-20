
#include <metal_stdlib>
using namespace metal;

half4 chooseColor(half4 color1, half4 color2, float cf) {
  if (cf > 0.5) {
    return color1;
  } else {
    return half4(color2 * (1 - cf) + color1 * cf);
  }
}

half4 chooseColor(half4 color1, int categoryIndex) {
  half4 color = half4(0,0,0,1);
  int colorIndex = categoryIndex - categoryIndex / 5;
  switch (colorIndex) {
    case 0:
      color = half4(1,0,0,1);
      break;
    case 1:
      color = half4(0,1,0,1);
      break;
    case 2:
      color = half4(0,0,1,1);
      break;
    case 3:
      color = half4(1,1,0,1);
      break;
    default:
      color = half4(0,1,1,1);
  }
  return  color1*0.5 + color*0.5;
//  if (colorIndex > 0) {
//    return color1 * 0.5 + color * 0.5;
//  } else {
//    return color1;
//  }
}

kernel void mergeColor(texture2d<half, access::read> inTexture [[ texture (0) ]],
                                  texture2d<half, access::read> inTexture2 [[ texture (1) ]],
                                  texture2d<half, access::read_write> outTexture [[ texture (2) ]],
                                  device uint8_t* data_in [[ buffer(0) ]],
                                  constant int& width [[buffer(1)]],
                                  uint2 gid [[ thread_position_in_grid ]]) {
  half4 color1 = inTexture.read(gid).rgba;
    int cf = data_in[gid.y * width + gid.x];
    half4 out = chooseColor(color1, cf);
    outTexture.write(out, gid);
}
