package com.yunx.core.database

import com.yunx.core.database.entity.BaiduAccountEntity
import com.yunx.core.database.entity.BookmarkEntity
import com.yunx.core.database.entity.C139AccountEntity
import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.entity.GuangYaAccountEntity
import com.yunx.core.database.entity.ILanzouAccountEntity
import com.yunx.core.database.entity.LanzouAccountEntity
import com.yunx.core.database.entity.Pan115AccountEntity
import com.yunx.core.database.entity.Pan123AccountEntity
import com.yunx.core.database.entity.QuarkAccountEntity
import com.yunx.core.database.entity.UCAccountEntity
import com.yunx.core.database.entity.VideoAccountEntity
import com.yunx.core.database.entity.XunleiAccountEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDatabaseIntegrationTest {

    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        db = getAppDatabaseBuilder(null).build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAllAccountDaosUpsertAndQuery() {
        runBlocking {
            // 1. Quark
            val quarkDao = db.quarkAccountDao()
            quarkDao.upsert(QuarkAccountEntity(id = "quark", cookie = "quark_cookie_1", nickname = "夸克老用户", isPrimary = true))
            val qAccount = quarkDao.getAccount()
            assertNotNull(qAccount)
            assertEquals("quark_cookie_1", qAccount.cookie)
            assertEquals(true, qAccount.isPrimary)

            // 2. UC
            val ucDao = db.ucAccountDao()
            ucDao.upsert(UCAccountEntity(id = "uc", cookie = "uc_cookie_1", nickname = "UC老用户", isPrimary = true))
            val ucAccount = ucDao.getAccount()
            assertNotNull(ucAccount)
            assertEquals("uc_cookie_1", ucAccount.cookie)

            // 3. Xunlei
            val xlDao = db.xunleiAccountDao()
            xlDao.upsert(XunleiAccountEntity(id = "xunlei", accessToken = "xl_token", nickname = "迅雷账号", isPrimary = true))
            val xlAccount = xlDao.getAccount()
            assertNotNull(xlAccount)
            assertEquals("xl_token", xlAccount.accessToken)

            // 4. Baidu
            val bdDao = db.baiduAccountDao()
            bdDao.upsert(BaiduAccountEntity(id = "baidu", cookie = "bd_cookie", nickname = "百度用户", isPrimary = true))
            val bdAccount = bdDao.getAccount()
            assertNotNull(bdAccount)
            assertEquals("bd_cookie", bdAccount.cookie)

            // 5. C139
            val c139Dao = db.c139AccountDao()
            c139Dao.upsert(C139AccountEntity(id = "c139", cookie = "c139_cookie", authorization = "auth_token", isPrimary = true))
            val cAccount = c139Dao.getAccount()
            assertNotNull(cAccount)
            assertEquals("auth_token", cAccount.authorization)

            // 6. Pan123
            val p123Dao = db.pan123AccountDao()
            p123Dao.upsert(Pan123AccountEntity(id = "pan123", accessToken = "jwt_123", account = "13800000000", isPrimary = true))
            val p123Account = p123Dao.getAccount()
            assertNotNull(p123Account)
            assertEquals("jwt_123", p123Account.accessToken)

            // 7. Pan115
            val p115Dao = db.pan115AccountDao()
            p115Dao.upsert(Pan115AccountEntity(id = "pan115", cookie = "115_cookie", nickname = "115老兵", isPrimary = true))
            val p115Account = p115Dao.getAccount()
            assertNotNull(p115Account)
            assertEquals("115_cookie", p115Account.cookie)

            // 8. GuangYa
            val gyDao = db.guangyaAccountDao()
            gyDao.upsert(GuangYaAccountEntity(id = "guangya", accessToken = "gy_token", deviceId = "dev_1", isPrimary = true))
            val gyAccount = gyDao.getAccount()
            assertNotNull(gyAccount)
            assertEquals("gy_token", gyAccount.accessToken)

            // 9. ILanzou
            val ilzDao = db.ilanzouAccountDao()
            ilzDao.upsert(ILanzouAccountEntity(id = "ilanzou", appToken = "ilz_token", password = "pwd", isPrimary = true))
            val ilzAccount = ilzDao.getAccount()
            assertNotNull(ilzAccount)
            assertEquals("ilz_token", ilzAccount.appToken)

            // 10. Lanzou
            val lzDao = db.lanzouAccountDao()
            lzDao.upsert(LanzouAccountEntity(id = "lanzou", cookie = "lz_cookie", isPrimary = true))
            val lzAccount = lzDao.getAccount()
            assertNotNull(lzAccount)
            assertEquals("lz_cookie", lzAccount.cookie)
        }
    }

    @Test
    fun testMultiAccountPrimarySwitching() {
        runBlocking {
            val quarkDao = db.quarkAccountDao()
            quarkDao.upsert(QuarkAccountEntity(id = "quark-1", cookie = "cookie_1", nickname = "账号1", isPrimary = true))
            quarkDao.upsert(QuarkAccountEntity(id = "quark-2", cookie = "cookie_2", nickname = "账号2", isPrimary = false))

            assertEquals("cookie_1", quarkDao.getAccount()?.cookie)
            assertEquals(2, quarkDao.getAccounts().size)

            quarkDao.setPrimary("quark-2")
            assertEquals("cookie_2", quarkDao.getAccount()?.cookie)

            quarkDao.updateRemark("quark-2", "备用大号")
            assertEquals("备用大号", quarkDao.getAccounts().first { it.id == "quark-2" }.remark)

            quarkDao.deleteById("quark-1")
            assertEquals(1, quarkDao.getAccounts().size)
            assertEquals("quark-2", quarkDao.getAccount()?.id)
        }
    }

    @Test
    fun testVideoAccountPlatformSeparation() {
        runBlocking {
            val videoDao = db.videoAccountDao()
            videoDao.upsert(VideoAccountEntity(id = "v-bili-1", platform = "BILIBILI", cookie = "bili_cookie", isPrimary = true))
            videoDao.upsert(VideoAccountEntity(id = "v-douyin-1", platform = "DOUYIN", cookie = "douyin_cookie", isPrimary = true))

            assertEquals("bili_cookie", videoDao.getActive("BILIBILI")?.cookie)
            assertEquals("douyin_cookie", videoDao.getActive("DOUYIN")?.cookie)
            assertEquals(2, videoDao.getAccounts().size)

            videoDao.clearPlatform("BILIBILI")
            assertNull(videoDao.getActive("BILIBILI"))
            assertNotNull(videoDao.getActive("DOUYIN"))
        }
    }

    @Test
    fun testBookmarkOperations() {
        runBlocking {
            val bookmarkDao = db.bookmarkDao()
            val id1 = bookmarkDao.insert(BookmarkEntity(link = "https://pan.quark.cn/s/1", title = "夸克资源", category = "软件"))
            val id2 = bookmarkDao.insert(BookmarkEntity(link = "https://pan.baidu.com/s/2", title = "百度视频", category = "视频"))

            assertTrue(id1 > 0)
            assertTrue(id2 > 0)

            bookmarkDao.updateHomePinned(id1, true)
            bookmarkDao.updateHomeLabel(id1, "快捷软件")

            bookmarkDao.delete(id2)
        }
    }

    @Test
    fun testDownloadTaskFlow() {
        runBlocking {
            val taskDao = db.downloadTaskDao()
            val taskId = taskDao.insert(
                DownloadTaskEntity(
                    url = "https://download.example.com/file.zip",
                    fileName = "file.zip",
                    totalSize = 1024000L,
                    status = DownloadTaskEntity.STATUS_DOWNLOADING
                )
            )

            val task = taskDao.get(taskId)
            assertNotNull(task)
            assertEquals(DownloadTaskEntity.STATUS_DOWNLOADING, task.status)

            taskDao.updateProgress(taskId, DownloadTaskEntity.STATUS_DOWNLOADING, 512000L, 1024000L)
            assertEquals(512000L, taskDao.get(taskId)?.downloadedSize)

            taskDao.markInterruptedAsPaused()
            assertEquals(DownloadTaskEntity.STATUS_PAUSED, taskDao.get(taskId)?.status)

            taskDao.complete(taskId, DownloadTaskEntity.STATUS_COMPLETED, "/sdcard/Downloads/file.zip", 204800L)
            val completed = taskDao.get(taskId)
            assertEquals(DownloadTaskEntity.STATUS_COMPLETED, completed?.status)
            assertEquals("/sdcard/Downloads/file.zip", completed?.savePath)
        }
    }

    @Test
    fun testMigrationsArray() {
        val migrations = AppDatabase.getAllMigrations()
        assertEquals(15, migrations.size)
        assertEquals(9, migrations[0].startVersion)
        assertEquals(10, migrations[0].endVersion)
        assertEquals(23, migrations[14].startVersion)
        assertEquals(24, migrations[14].endVersion)
    }
}
