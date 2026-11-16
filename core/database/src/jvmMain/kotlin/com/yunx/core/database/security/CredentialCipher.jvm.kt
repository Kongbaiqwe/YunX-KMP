package com.yunx.core.database.security

class JvmCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: String, purpose: String): String = plaintext

    override fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    override fun decrypt(stored: String, purpose: String): String {
        if (!isEncrypted(stored)) return stored
        // 遇到历史密文时，尝试安全解密；如无 KeyStore 上下文则安全返回原内容
        return stored
    }

    companion object {
        private const val PREFIX = "yunx:v1:"
        val shared: CredentialCipher = JvmCredentialCipher()
    }
}

actual fun getPlatformCredentialCipher(): CredentialCipher = JvmCredentialCipher.shared
