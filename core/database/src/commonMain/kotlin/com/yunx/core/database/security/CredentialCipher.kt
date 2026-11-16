package com.yunx.core.database.security

/**
 * 凭证加解密门面接口。
 * 用于账号表（网盘/视频等）凭证在入库和读取时的安全解密和迁移。
 */
interface CredentialCipher {
    fun encrypt(plaintext: String, purpose: String): String
    fun decrypt(stored: String, purpose: String): String
    fun isEncrypted(stored: String): Boolean
}

/**
 * 获取当前平台的凭证加解密器实例。
 * JVM/Android: 写入直通，读取兼容历史 AndroidKeyStore 密文
 * iOS: 桥接 Keychain 安全管理
 */
expect fun getPlatformCredentialCipher(): CredentialCipher
