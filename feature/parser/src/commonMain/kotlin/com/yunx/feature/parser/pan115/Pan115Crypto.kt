package com.yunx.feature.parser.pan115

import com.yunx.core.common.PlatformCrypto
import kotlin.random.Random

/**
 * 115 电脑端下载接口加解密（纯 KMP，跨平台支持）。
 */
object Pan115Crypto {
    private val KEY_TABLE = intArrayOf(
        0xf0, 0xe5, 0x69, 0xae, 0xbf, 0xdc, 0xbf, 0x8a,
        0x1a, 0x45, 0xe8, 0xbe, 0x7d, 0xa6, 0x73, 0xb8,
        0xde, 0x8f, 0xe7, 0xc4, 0x45, 0xda, 0x86, 0xc4,
        0x9b, 0x64, 0x8b, 0x14, 0x6a, 0xb4, 0xf1, 0xaa,
        0x38, 0x01, 0x35, 0x9e, 0x26, 0x69, 0x2c, 0x86,
        0x00, 0x6b, 0x4f, 0xa5, 0x36, 0x34, 0x62, 0xa6,
        0x2a, 0x96, 0x68, 0x18, 0xf2, 0x4a, 0xfd, 0xbd,
        0x6b, 0x97, 0x8f, 0x4d, 0x8f, 0x89, 0x13, 0xb7,
        0x6c, 0x8e, 0x93, 0xed, 0x0e, 0x0d, 0x48, 0x3e,
        0xd7, 0x2f, 0x88, 0xd8, 0xfe, 0xfe, 0x7e, 0x86,
        0x50, 0x95, 0x4f, 0xd1, 0xeb, 0x83, 0x26, 0x34,
        0xdb, 0x66, 0x7b, 0x9c, 0x7e, 0x9d, 0x7a, 0x81,
        0x32, 0xea, 0xb6, 0x33, 0xde, 0x3a, 0xa9, 0x59,
        0x34, 0x66, 0x3b, 0xaa, 0xba, 0x81, 0x60, 0x48,
        0xb9, 0xd5, 0x81, 0x9c, 0xf8, 0x6c, 0x84, 0x77,
        0xff, 0x54, 0x78, 0x26, 0x5f, 0xbe, 0xe8, 0x1e,
        0x36, 0x9f, 0x34, 0x80, 0x5c, 0x45, 0x2c, 0x9b,
        0x76, 0xd5, 0x1b, 0x8f, 0xcc, 0xc3, 0xb8, 0xf5
    )

    private val LONG_KEY = intArrayOf(
        0x78, 0x06, 0xad, 0x4c, 0x33, 0x86, 0x5d, 0x18, 0x4c, 0x01, 0x3f, 0x46
    )

    private const val MODULUS_HEX =
        "8686980c0f5a24c4b9d43020cd2c22703ff3f450756529058b1cf88f09b86021" +
        "36477198a6e2683149659bd122c33592fdb5ad47944ad1ea4d36c6b172aad633" +
        "8c3bb6ac6227502d010993ac967d1aef00f0c8e038de2e4d3bc2ec368af2e9f1" +
        "0a6f1eda4f7262f136420c07c331b871bf139f74f3010e3c4fe57df3afb71683"

    private const val EXPONENT_HEX = "10001"
    private const val RSA_ENCODE_BLOCK = 117
    private const val RSA_BLOCK = 128

    fun newKey(): ByteArray = Random.nextBytes(16)

    fun encode(payload: String, key: ByteArray): String {
        require(key.size == 16) { "115 协议 key 必须为 16 字节" }
        val body = payload.encodeToByteArray().copyOf()
        symmetricEncode(body, key)
        val plain = ByteArray(16 + body.size)
        key.copyInto(plain, 0, 0, 16)
        body.copyInto(plain, 16, 0, body.size)
        return PlatformCrypto.base64Encode(rsaEncrypt(plain))
    }

    fun decode(data: String, key: ByteArray): String {
        require(key.size == 16) { "115 协议 key 必须为 16 字节" }
        val decrypted = rsaDecrypt(PlatformCrypto.base64Decode(data))
        check(decrypted.size > 16) { "115 响应长度异常" }
        val salt = decrypted.copyOfRange(0, 16)
        val body = decrypted.copyOfRange(16, decrypted.size)
        symmetricDecode(body, key, salt)
        return body.decodeToString()
    }

    private fun symmetricEncode(data: ByteArray, key: ByteArray) {
        xorTransform(data, deriveKey(key, 4))
        data.reverse()
        xorTransform(data, ByteArray(LONG_KEY.size) { LONG_KEY[it].toByte() })
    }

    private fun symmetricDecode(data: ByteArray, key: ByteArray, salt: ByteArray) {
        xorTransform(data, deriveKey(salt, 12))
        data.reverse()
        xorTransform(data, deriveKey(key, 4))
    }

    private fun deriveKey(seed: ByteArray, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            val left = KEY_TABLE[size * i]
            val right = KEY_TABLE[size * (size - 1 - i)]
            out[i] = (((seed[i % seed.size].toInt() and 0xff) + left) and 0xff xor right).toByte()
        }
        return out
    }

    private fun xorTransform(data: ByteArray, key: ByteArray) {
        val offset = data.size % 4
        for (i in 0 until offset) {
            data[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        for (i in offset until data.size) {
            data[i] = (data[i].toInt() xor key[(i - offset) % key.size].toInt()).toByte()
        }
    }

    private fun rsaEncrypt(input: ByteArray): ByteArray {
        val outList = mutableListOf<Byte>()
        var offset = 0
        while (offset < input.size) {
            val size = minOf(RSA_ENCODE_BLOCK, input.size - offset)
            val block = ByteArray(RSA_BLOCK) { 0xff.toByte() }
            block[0] = 0
            block[1] = 2
            val separator = RSA_BLOCK - size - 1
            block[separator] = 0
            input.copyInto(block, separator + 1, offset, offset + size)
            val encryptedBlock = PlatformCrypto.rsaModPow(block, EXPONENT_HEX, MODULUS_HEX, RSA_BLOCK)
            for (b in encryptedBlock) outList.add(b)
            offset += size
        }
        return outList.toByteArray()
    }

    private fun rsaDecrypt(input: ByteArray): ByteArray {
        val outList = mutableListOf<Byte>()
        var offset = 0
        while (offset < input.size) {
            val size = minOf(RSA_BLOCK, input.size - offset)
            val block = input.copyOfRange(offset, offset + size)
            val plain = PlatformCrypto.rsaModPow(block, EXPONENT_HEX, MODULUS_HEX, RSA_BLOCK)
            var start = -1
            for (i in 2 until plain.size) {
                if (plain[i] == 0.toByte()) {
                    start = i + 1
                    break
                }
            }
            check(start in 1 until plain.size) { "115 RSA 解密缺少有效分隔符" }
            for (i in start until plain.size) {
                outList.add(plain[i])
            }
            offset += size
        }
        return outList.toByteArray()
    }
}
