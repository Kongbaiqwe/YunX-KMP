package com.yunx.shared

import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.getAppDatabaseBuilder
import com.yunx.core.model.SharePlatform
import com.yunx.feature.download.engine.DownloadEngine
import com.yunx.feature.download.engine.EngineTaskParams
import com.yunx.feature.download.engine.EngineTaskStatus
import com.yunx.feature.download.engine.EngineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YunXDownloadCoordinatorTest {

    private class MockEngine : DownloadEngine {
        override val type: EngineType = EngineType.GOPEED
        var paused = false
        var resumed = false

        override suspend fun isAvailable(): Boolean = true
        override suspend fun startEngine(): Boolean = true
        override suspend fun stopEngine(): Boolean = true

        override suspend fun createTask(params: EngineTaskParams): String = "mock_engine_task_1"
        override suspend fun pauseTask(engineTaskId: String): Boolean {
            paused = true
            return true
        }
        override suspend fun resumeTask(engineTaskId: String): Boolean {
            resumed = true
            return true
        }
        override suspend fun deleteTask(engineTaskId: String): Boolean = true
        override suspend fun getTaskStatus(engineTaskId: String): EngineTaskStatus {
            return EngineTaskStatus("mock_engine_task_1", "downloading", 500L, 1000L, 100L)
        }
    }

    @Test
    fun testResolveDownloadLink() = runBlocking {
        val coord = YunXDownloadCoordinator(
            database = getAppDatabaseBuilder().build(),
            downloadEngine = MockEngine()
        )

        // 1. 直链解析
        val direct = coord.resolveDownloadLink("https://example.com/files/app-release.ipa?token=abc")
        assertNotNull(direct)
        assertEquals("app-release.ipa", direct.filename)
        assertEquals("https://example.com/files/app-release.ipa?token=abc", direct.downloadUrl)

        // 2. 夸克分享链接解析
        val quarkShare = coord.resolveDownloadLink("夸克网盘：https://pan.quark.cn/s/abcdef987654 提取码: 6666")
        assertNotNull(quarkShare)
        assertEquals("abcdef987654", quarkShare.fid)
        assertEquals("quark_abcdef987654", quarkShare.filename)
    }

    @Test
    fun testEndToEndTaskPersistenceAndResume() = runBlocking {
        val db = getAppDatabaseBuilder().build()
        val engine = MockEngine()
        val coord = YunXDownloadCoordinator(database = db, downloadEngine = engine)

        // 1. 创建下载任务入库并自启动
        val taskId = coord.createAndStartDownload(
            url = "https://example.com/testfile.bin",
            fileName = "testfile.bin",
            platform = "direct",
            totalSize = 1024L
        )
        assertTrue(taskId > 0)

        // 2. 从 Room 查询任务状态
        val task = coord.getTask(taskId)
        assertNotNull(task)
        assertEquals("testfile.bin", task.fileName)
        assertEquals(1024L, task.totalSize)

        // 3. 观察任务列表 Flow
        val tasks = coord.observeTasks().first()
        assertTrue(tasks.any { it.id == taskId })

        // 4. 暂停任务（断点测试）
        coord.pauseTask(taskId)
        val pausedTask = coord.getTask(taskId)
        assertNotNull(pausedTask)
        assertEquals(DownloadTaskEntity.STATUS_PAUSED, pausedTask.status)

        // 5. 恢复任务（断点续传流转）
        coord.resumeTask(taskId)
        val resumedTask = coord.getTask(taskId)
        assertNotNull(resumedTask)
        // 恢复后任务状态已脱离 PAUSED，进入恢复流转
        assertTrue(resumedTask.status != DownloadTaskEntity.STATUS_PAUSED)

        // 6. 验证引擎信息获取
        val info = coord.getEngineInfo()
        assertTrue(info.isNotBlank())
    }

    @Test
    fun testDownloadSettingsPersistAcrossRepositoryInstances() {
        val repo = DownloadSettingsRepository()
        val originalConcurrency = repo.maxConcurrentDownloads
        val originalEngine = repo.downloadEngine

        try {
            repo.maxConcurrentDownloads = 5
            repo.downloadEngine = DownloadSettingsRepository.ENGINE_BUILTIN

            val reloaded = DownloadSettingsRepository()
            assertEquals(5, reloaded.maxConcurrentDownloads)
            assertEquals(DownloadSettingsRepository.ENGINE_BUILTIN, reloaded.downloadEngine)
        } finally {
            repo.maxConcurrentDownloads = originalConcurrency
            repo.downloadEngine = originalEngine
        }
    }
}
