package com.yunx.core.database.secure

import com.yunx.core.database.dao.BaiduAccountDao
import com.yunx.core.database.dao.C139AccountDao
import com.yunx.core.database.dao.GuangYaAccountDao
import com.yunx.core.database.dao.ILanzouAccountDao
import com.yunx.core.database.dao.LanzouAccountDao
import com.yunx.core.database.dao.Pan115AccountDao
import com.yunx.core.database.dao.Pan123AccountDao
import com.yunx.core.database.dao.QuarkAccountDao
import com.yunx.core.database.dao.UCAccountDao
import com.yunx.core.database.dao.VideoAccountDao
import com.yunx.core.database.dao.XunleiAccountDao
import com.yunx.core.database.entity.BaiduAccountEntity
import com.yunx.core.database.entity.C139AccountEntity
import com.yunx.core.database.entity.GuangYaAccountEntity
import com.yunx.core.database.entity.ILanzouAccountEntity
import com.yunx.core.database.entity.LanzouAccountEntity
import com.yunx.core.database.entity.Pan115AccountEntity
import com.yunx.core.database.entity.Pan123AccountEntity
import com.yunx.core.database.entity.QuarkAccountEntity
import com.yunx.core.database.entity.UCAccountEntity
import com.yunx.core.database.entity.VideoAccountEntity
import com.yunx.core.database.entity.XunleiAccountEntity
import com.yunx.core.database.security.CredentialCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DAO 装饰器：明文落库，读取时兼容历史密文并自动明文回写覆盖。
 * 跨平台纯 Kotlin 实现，完全剥离 AndroidKeyStore / android.*。
 */
object SecureAccountDaos {
    private const val TAG = "YunX-SecureDao"

    fun quark(raw: QuarkAccountDao, cipher: CredentialCipher): QuarkAccountDao = object : QuarkAccountDao {
        override fun observeAccount(): Flow<QuarkAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptQuark(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: QuarkAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): QuarkAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptQuark(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<QuarkAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptQuark(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<QuarkAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptQuark(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun uc(raw: UCAccountDao, cipher: CredentialCipher): UCAccountDao = object : UCAccountDao {
        override fun observeAccount(): Flow<UCAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptUc(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: UCAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): UCAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptUc(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<UCAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptUc(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<UCAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptUc(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun baidu(raw: BaiduAccountDao, cipher: CredentialCipher): BaiduAccountDao = object : BaiduAccountDao {
        override fun observeAccount(): Flow<BaiduAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptBaidu(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: BaiduAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): BaiduAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptBaidu(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<BaiduAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptBaidu(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<BaiduAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptBaidu(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun c139(raw: C139AccountDao, cipher: CredentialCipher): C139AccountDao = object : C139AccountDao {
        override fun observeAccount(): Flow<C139AccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptC139(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: C139AccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): C139AccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptC139(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<C139AccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptC139(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<C139AccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptC139(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun pan123(raw: Pan123AccountDao, cipher: CredentialCipher): Pan123AccountDao = object : Pan123AccountDao {
        override fun observeAccount(): Flow<Pan123AccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptPan123(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: Pan123AccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): Pan123AccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptPan123(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<Pan123AccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptPan123(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<Pan123AccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptPan123(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun pan115(raw: Pan115AccountDao, cipher: CredentialCipher): Pan115AccountDao = object : Pan115AccountDao {
        override fun observeAccount(): Flow<Pan115AccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptPan115(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: Pan115AccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): Pan115AccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptPan115(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<Pan115AccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptPan115(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<Pan115AccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptPan115(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun guangya(raw: GuangYaAccountDao, cipher: CredentialCipher): GuangYaAccountDao = object : GuangYaAccountDao {
        override fun observeAccount(): Flow<GuangYaAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptGuangYa(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: GuangYaAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): GuangYaAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptGuangYa(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<GuangYaAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptGuangYa(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<GuangYaAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptGuangYa(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun ilanzou(raw: ILanzouAccountDao, cipher: CredentialCipher): ILanzouAccountDao = object : ILanzouAccountDao {
        override fun observeAccount(): Flow<ILanzouAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptILanzou(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: ILanzouAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): ILanzouAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptILanzou(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<ILanzouAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptILanzou(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<ILanzouAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptILanzou(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun lanzou(raw: LanzouAccountDao, cipher: CredentialCipher): LanzouAccountDao = object : LanzouAccountDao {
        override fun observeAccount(): Flow<LanzouAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptLanzou(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: LanzouAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): LanzouAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptLanzou(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<LanzouAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptLanzou(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<LanzouAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptLanzou(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun xunlei(raw: XunleiAccountDao, cipher: CredentialCipher): XunleiAccountDao = object : XunleiAccountDao {
        override fun observeAccount(): Flow<XunleiAccountEntity?> = raw.observeAccount().map { value ->
            value?.let { decryptOrNull { decryptXunlei(raw, cipher, it) } }
        }.catch { emit(null) }

        override suspend fun upsert(account: XunleiAccountEntity) = raw.upsert(account)

        override suspend fun getAccount(): XunleiAccountEntity? = decryptOrNull {
            raw.getAccount()?.let { decryptXunlei(raw, cipher, it) }
        }

        override fun observeAccounts(): Flow<List<XunleiAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptXunlei(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<XunleiAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptXunlei(raw, cipher, it) } }
        } ?: emptyList()

        override suspend fun setPrimary(id: String) = raw.setPrimary(id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clear() = raw.clear()
    }

    fun videoAccount(raw: VideoAccountDao, cipher: CredentialCipher): VideoAccountDao = object : VideoAccountDao {
        override fun observeAccounts(): Flow<List<VideoAccountEntity>> = raw.observeAccounts().map { list ->
            list.mapNotNull { decryptOrNull { decryptVideoAccount(raw, cipher, it) } }
        }.catch { emit(emptyList()) }

        override suspend fun getAccounts(): List<VideoAccountEntity> = decryptOrNull {
            raw.getAccounts().mapNotNull { decryptOrNull { decryptVideoAccount(raw, cipher, it) } }
        } ?: emptyList()

        override fun observeAccountsOf(platform: String): Flow<List<VideoAccountEntity>> =
            raw.observeAccountsOf(platform).map { list ->
                list.mapNotNull { decryptOrNull { decryptVideoAccount(raw, cipher, it) } }
            }.catch { emit(emptyList()) }

        override suspend fun getActive(platform: String): VideoAccountEntity? = decryptOrNull {
            raw.getActive(platform)?.let { decryptVideoAccount(raw, cipher, it) }
        }

        override suspend fun countOf(platform: String): Int = raw.countOf(platform)
        override suspend fun upsert(account: VideoAccountEntity) = raw.upsert(account)
        override suspend fun setPrimary(platform: String, id: String) = raw.setPrimary(platform, id)
        override suspend fun updateRemark(id: String, remark: String) = raw.updateRemark(id, remark)
        override suspend fun deleteById(id: String) = raw.deleteById(id)
        override suspend fun clearPlatform(platform: String) = raw.clearPlatform(platform)
    }

    // ---------- 内部解密与明文回写 ----------

    private suspend fun decryptQuark(raw: QuarkAccountDao, cipher: CredentialCipher, stored: QuarkAccountEntity): QuarkAccountEntity {
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "quark.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptUc(raw: UCAccountDao, cipher: CredentialCipher, stored: UCAccountEntity): UCAccountEntity {
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "uc.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptBaidu(raw: BaiduAccountDao, cipher: CredentialCipher, stored: BaiduAccountEntity): BaiduAccountEntity {
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "baidu.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptC139(raw: C139AccountDao, cipher: CredentialCipher, stored: C139AccountEntity): C139AccountEntity {
        val plain = stored.copy(
            cookie = cipher.decrypt(stored.cookie, "c139.cookie"),
            authorization = cipher.decrypt(stored.authorization, "c139.authorization")
        )
        if (cipher.isEncrypted(stored.cookie) || cipher.isEncrypted(stored.authorization)) {
            raw.upsert(plain)
        }
        return plain
    }

    private suspend fun decryptPan123(raw: Pan123AccountDao, cipher: CredentialCipher, stored: Pan123AccountEntity): Pan123AccountEntity {
        val plain = stored.copy(accessToken = cipher.decrypt(stored.accessToken, "pan123.accessToken"))
        if (cipher.isEncrypted(stored.accessToken)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptPan115(raw: Pan115AccountDao, cipher: CredentialCipher, stored: Pan115AccountEntity): Pan115AccountEntity {
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "pan115.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptGuangYa(raw: GuangYaAccountDao, cipher: CredentialCipher, stored: GuangYaAccountEntity): GuangYaAccountEntity {
        val plain = stored.copy(
            accessToken = cipher.decrypt(stored.accessToken, "guangya.accessToken"),
            refreshToken = cipher.decrypt(stored.refreshToken, "guangya.refreshToken"),
            deviceId = cipher.decrypt(stored.deviceId, "guangya.deviceId"),
            deviceSign = cipher.decrypt(stored.deviceSign, "guangya.deviceSign")
        )
        if (listOf(stored.accessToken, stored.refreshToken, stored.deviceId, stored.deviceSign).any { cipher.isEncrypted(it) }) {
            raw.upsert(plain)
        }
        return plain
    }

    private suspend fun decryptILanzou(raw: ILanzouAccountDao, cipher: CredentialCipher, stored: ILanzouAccountEntity): ILanzouAccountEntity {
        val plain = stored.copy(
            appToken = cipher.decrypt(stored.appToken, "ilanzou.appToken"),
            password = cipher.decrypt(stored.password, "ilanzou.password")
        )
        if (cipher.isEncrypted(stored.appToken) || cipher.isEncrypted(stored.password)) {
            raw.upsert(plain)
        }
        return plain
    }

    private suspend fun decryptLanzou(raw: LanzouAccountDao, cipher: CredentialCipher, stored: LanzouAccountEntity): LanzouAccountEntity {
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "lanzou.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun decryptXunlei(raw: XunleiAccountDao, cipher: CredentialCipher, stored: XunleiAccountEntity): XunleiAccountEntity {
        val plain = stored.copy(
            accessToken = cipher.decrypt(stored.accessToken, "xunlei.accessToken"),
            refreshToken = cipher.decrypt(stored.refreshToken, "xunlei.refreshToken"),
            deviceId = cipher.decrypt(stored.deviceId, "xunlei.deviceId"),
            captchaToken = cipher.decrypt(stored.captchaToken, "xunlei.captchaToken")
        )
        if (listOf(stored.accessToken, stored.refreshToken, stored.deviceId, stored.captchaToken).any { cipher.isEncrypted(it) }) {
            raw.upsert(plain)
        }
        return plain
    }

    private suspend fun decryptVideoAccount(raw: VideoAccountDao, cipher: CredentialCipher, stored: VideoAccountEntity): VideoAccountEntity {
        if (stored.cookie.isBlank()) return stored
        val plain = stored.copy(cookie = cipher.decrypt(stored.cookie, "video.cookie"))
        if (cipher.isEncrypted(stored.cookie)) raw.upsert(plain)
        return plain
    }

    private suspend fun <T> decryptOrNull(block: suspend () -> T?): T? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println("[$TAG] 凭证读取失败（按未登录处理，数据保留）: ${error.message}")
            null
        }
}
