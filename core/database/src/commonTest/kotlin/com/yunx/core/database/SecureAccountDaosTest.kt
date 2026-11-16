package com.yunx.core.database

import com.yunx.core.database.dao.QuarkAccountDao
import com.yunx.core.database.entity.QuarkAccountEntity
import com.yunx.core.database.secure.SecureAccountDaos
import com.yunx.core.database.security.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SecureAccountDaosTest {

    @Test
    fun writesPlaintextAndReadsItBackUnchanged() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "cookie"))
        val secure = SecureAccountDaos.quark(raw, FakeCipher())

        assertEquals("cookie", secure.getAccount()?.cookie)

        secure.upsert(QuarkAccountEntity(cookie = "new-secret"))
        assertEquals("new-secret", secure.getAccount()?.cookie)
        assertEquals("new-secret", raw.value()?.cookie)
    }

    @Test
    fun plaintextReadDoesNotRewriteRow() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "cookie"))
        val secure = SecureAccountDaos.quark(raw, FakeCipher())

        assertEquals("cookie", secure.getAccount()?.cookie)
        assertEquals("cookie", secure.getAccounts().single().cookie)
        assertEquals(0, raw.upsertCount)
    }

    @Test
    fun legacyCiphertextIsDecryptedAndRewrittenAsPlaintext() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "sealed:cookie"))
        val secure = SecureAccountDaos.quark(raw, FakeCipher())

        assertEquals("cookie", secure.getAccount()?.cookie)
        assertEquals("cookie", raw.value()?.cookie)
        assertEquals(1, raw.upsertCount)
        assertFalse(raw.value()?.cookie?.startsWith("sealed:") == true)
    }

    @Test
    fun brokenCiphertextReturnsNullAndKeepsRow() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "sealed:cookie"))
        val secure = SecureAccountDaos.quark(raw, CorruptCipher())

        assertNull(secure.getAccount())
        assertEquals("sealed:cookie", raw.value()?.cookie)
    }

    @Test
    fun observeAccountSwallowsDecryptFailure() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "sealed:cookie"))
        val secure = SecureAccountDaos.quark(raw, ThrowingCipher())

        assertNull(secure.observeAccount().first())
        assertEquals("sealed:cookie", raw.value()?.cookie)
    }

    @Test
    fun unavailableKeyKeepsStoredCredential() = runBlocking {
        val raw = FakeQuarkDao(QuarkAccountEntity(cookie = "sealed:cookie"))
        val secure = SecureAccountDaos.quark(raw, ThrowingCipher())

        assertNull(secure.getAccount())
        assertEquals("sealed:cookie", raw.value()?.cookie)
    }

    private class FakeQuarkDao(initial: QuarkAccountEntity?) : QuarkAccountDao {
        private val state = MutableStateFlow(initial)

        var upsertCount = 0
            private set

        fun value(): QuarkAccountEntity? = state.value
        override fun observeAccount(): Flow<QuarkAccountEntity?> = state
        override suspend fun upsert(account: QuarkAccountEntity) {
            upsertCount++
            state.value = account
        }

        override suspend fun getAccount(): QuarkAccountEntity? = state.value
        override fun observeAccounts(): Flow<List<QuarkAccountEntity>> =
            state.map { listOfNotNull(it) }

        override suspend fun getAccounts(): List<QuarkAccountEntity> = listOfNotNull(state.value)

        override suspend fun setPrimary(id: String) {
            val current = state.value ?: return
            state.value = current.copy(isPrimary = current.id == id)
        }

        override suspend fun updateRemark(id: String, remark: String) {
            val current = state.value ?: return
            if (current.id == id) state.value = current.copy(remark = remark)
        }

        override suspend fun deleteById(id: String) {
            if (state.value?.id == id) state.value = null
        }

        override suspend fun clear() { state.value = null }
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plaintext: String, purpose: String): String = plaintext
        override fun decrypt(stored: String, purpose: String): String =
            if (isEncrypted(stored)) stored.removePrefix("sealed:") else stored
        override fun isEncrypted(stored: String): Boolean = stored.startsWith("sealed:")
    }

    private class CorruptCipher : CredentialCipher {
        override fun encrypt(plaintext: String, purpose: String): String = plaintext
        override fun decrypt(stored: String, purpose: String): String =
            throw IllegalStateException("GCM tag mismatch")
        override fun isEncrypted(stored: String): Boolean = stored.startsWith("sealed:")
    }

    private class ThrowingCipher : CredentialCipher {
        override fun encrypt(plaintext: String, purpose: String): String = plaintext
        override fun decrypt(stored: String, purpose: String): String =
            throw IllegalStateException("旧加密密钥已不存在，无法解密历史凭证")
        override fun isEncrypted(stored: String): Boolean = stored.startsWith("sealed:")
    }
}
