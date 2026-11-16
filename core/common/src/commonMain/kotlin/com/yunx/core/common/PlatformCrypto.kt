package com.yunx.core.common

expect object PlatformCrypto {
    fun md5(data: ByteArray): ByteArray
    fun md5Hex(data: ByteArray): String
    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray
    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray
    fun rsaModPow(input: ByteArray, exponentHex: String, modulusHex: String, outputBlockSize: Int = 128): ByteArray
    fun base64Encode(data: ByteArray): String
    fun base64Decode(text: String): ByteArray
}
