
#include <metal_stdlib>
using namespace metal;

constant half4 legendColors[] = {
  {1.0000, 0.7725, 0.0000, 1}, //Vivid Yellow
  {0.5020, 0.2431, 0.4588, 1}, //Strong Purple
  {1.0000, 0.4078, 0.0000, 1}, //Vivid Orange
  {0.6510, 0.7412, 0.8431, 1}, //Very Light Blue
  {0.7569, 0.0000, 0.1255, 1}, //Vivid Red
  {0.8078, 0.6353, 0.3843, 1}, //Grayish Yellow
  {0.5059, 0.4392, 0.4000, 1}, //Medium Gray
  {0.0000, 0.4902, 0.2039, 1}, //Vivid Green
  {0.9647, 0.4627, 0.5569, 1}, //Strong Purplish Pink
  {0.0000, 0.3255, 0.5412, 1}, //Strong Blue
  {1.0000, 0.4392, 0.3608, 1}, //Strong Yellowish Pink
  {0.3255, 0.2157, 0.4392, 1}, //Strong Violet
  {1.0000, 0.5569, 0.0000, 1}, //Vivid Orange Yellow
  {0.7020, 0.1569, 0.3176, 1}, //Strong Purplish Red
  {0.9569, 0.7843, 0.0000, 1}, //Vivid Greenish Yellow
  {0.4980, 0.0941, 0.0510, 1}, //Strong Reddish Brown
  {0.5765, 0.6667, 0.0000, 1}, //Vivid Yellowish Green
  {0.3490, 0.2000, 0.0824, 1}, //Deep Yellowish Brown
  {0.9451, 0.2275, 0.0745, 1}, //Vivid Reddish Orange
  {0.1373, 0.1725, 0.0863, 1}, //Dark Olive Green
  {0.0000, 0.6314, 0.7608, 1}, //Vivid Blue
};

half4 mergeColor(half4 pixelData, int categoryIndex) {
  half4 color = legendColors[categoryIndex];
  return  pixelData*0.5 + color*0.5;
}

kernel void mergeColor(texture2d<half, access::read> inTexture [[ texture (0) ]],
                       texture2d<half, access::read_write> outTexture [[ texture (1) ]],
                       device uint8_t* data_in [[ buffer(0) ]],
                       constant int& width [[buffer(1)]],
                       uint2 gid [[ thread_position_in_grid ]]) {
  half4 pixelData = inTexture.read(gid).rgba;
  uint8_t categoryIndex = data_in[gid.y * width + gid.x];
  half4 outputPixelData = mergeColor(pixelData, categoryIndex);
  outTexture.write(outputPixelData, gid);
}
