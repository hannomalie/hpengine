package de.hanno.hpengine

// -15 stored using a single-precision bias of 127  (127 - 15)
const val HALF_FLOAT_MIN_BIASED_EXP_AS_SINGLE_FP_EXP = 0x38000000

// max exponent value in single precision that will be converted to Inf or NaN when stored as a half-float
// 127 + 16 = 143 (1000 1111)
const val HALF_FLOAT_MAX_BIASED_EXP_AS_SINGLE_FP_EXP = 0x47800000

const val FLOAT_MAX_BIASED_EXP = 0xFF shl 23
const val HALF_FLOAT_MAX_BIASED_EXP = 0x1F shl 10


// https://gist.github.com/halls/791fa22dd883c7a179b49631832b5048#file-floattohalffloat-kt
fun Float.toHalfFloat(): Short {
    val x = toRawBits()
    val sign = x ushr 31
    var mantissa = x and ((1 shl 23) - 1)
    var exp = x and FLOAT_MAX_BIASED_EXP

    // Infinities and NaNs
    if (exp >= HALF_FLOAT_MAX_BIASED_EXP_AS_SINGLE_FP_EXP) {
        // check if the original single-precision float number is NaN
        // if (f.isNaN())
        if (mantissa != 0 && exp == FLOAT_MAX_BIASED_EXP) {
            // single precision NaN, preserving a leading bit (quiet vs signalling)
            mantissa = mantissa ushr 13
            mantissa = if (mantissa == 0) 1 else mantissa
        } else {
            // 16-bit half-float representation stores number as Inf
            mantissa = 0
        }
        return (sign shl 15 or HALF_FLOAT_MAX_BIASED_EXP or mantissa).toShort()
        // Denormals and zeros
    } else if (exp <= HALF_FLOAT_MIN_BIASED_EXP_AS_SINGLE_FP_EXP) { // check if exponent is <= -15
        // store a denormalized half-float value or zero
        exp = (HALF_FLOAT_MIN_BIASED_EXP_AS_SINGLE_FP_EXP - exp) ushr 23
        mantissa = mantissa or (1 shl 23) // explicitly setting implicit high-order bit of the mantissa to 1 before converting to denormal
        mantissa = mantissa ushr (14 + exp) // 14 = (23 - 10) + 1 (plus 1 because it's denormalized - convert 1.xxx to 0.x1x)
        return  (sign shl 15 or mantissa).toShort()
        // Regular range
    } else {
        return (sign shl 15 or ((exp - HALF_FLOAT_MIN_BIASED_EXP_AS_SINGLE_FP_EXP) ushr 13) or (mantissa ushr 13)).toShort()
    }
}