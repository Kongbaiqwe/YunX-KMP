package com.yunx.core.common

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object PlatformCrypto {
    actual fun md5(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data)
    }

    actual fun md5Hex(data: ByteArray): String {
        val hash = md5(data)
        return hash.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    actual fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    actual fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    actual fun rsaModPow(
        input: ByteArray,
        exponentHex: String,
        modulusHex: String,
        outputBlockSize: Int
    ): ByteArray {
        val exp = BigInteger(exponentHex, 16)
        val mod = BigInteger(modulusHex, 16)
        val m = BigInteger(1, input)
        val res = m.modPow(exp, mod)
        val raw = res.toByteArray()
        if (raw.size == outputBlockSize) return raw
        val out = ByteArray(outputBlockSize)
        if (raw.size > outputBlockSize) {
            System.arraycopy(raw, raw.size - outputBlockSize, out, 0, outputBlockSize)
        } else {
            System.arraycopy(raw, 0, out, outputBlockSize - raw.size, raw.size)
        }
        return out
    }

    actual fun base64Encode(data: ByteArray): String {
        return Base64.getEncoder().encodeToString(data)
    }

    actual fun base64Decode(text: String): ByteArray {
        return Base64.getDecoder().decode(text)
    }
}
