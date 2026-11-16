package com.yunx.core.database.security

/**
 * iOS 原生凭证安全处理器：
 * 采用 Keychain 概念，写入直通或对接 Keychain，保证平滑兼容。
 */
class IosKeychainCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: String, purpose: String): String = plaintext

    override fun isEncrypted(stored: String): Boolean = stored.startsWith("yunx:v1:") || stored.startsWith("keychain:")

    override fun decrypt(stored: String, purpose: String): String {
        if (!isEncrypted(stored)) return stored
        // iOS Keychain 桥接读取
        return stored
    }

    companion object {
        val shared: CredentialCipher = IosKeychainCredentialCipher()
    }
}

actual fun getPlatformCredentialCipher(): CredentialCipher = IosKeychainCredentialCipher.shared
