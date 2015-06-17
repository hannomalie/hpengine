__kernel void sum(__global const float* a, __global const float* b, __global float* result, int const size) {
   const int itemId = get_global_id(0); 
   if(itemId < size) {
      result[itemId] = a[itemId] + b[itemId];
   }
}    