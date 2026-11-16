package com.yunx.core.common

actual object PlatformCrypto {
    actual fun md5(data: ByteArray): ByteArray {
        // iOS CommonCrypto 或占位
        return data.take(16).toByteArray()
    }

    actual fun md5Hex(data: ByteArray): String {
        return md5(data).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    actual fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        return data
    }

    actual fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        return data
    }

    actual fun rsaModPow(
        input: ByteArray,
        exponentHex: String,
        modulusHex: String,
        outputBlockSize: Int
    ): ByteArray {
        return ByteArray(outputBlockSize)
    }

    actual fun base64Encode(data: ByteArray): String {
        return ""
    }

    actual fun base64Decode(text: String): ByteArray {
        return ByteArray(0)
    }
}
