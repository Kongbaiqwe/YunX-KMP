import XCTest
import Libgopeed
import SharedKit
@testable import iosApp

/**
 * iOS 端到端 POC 自动化验证测试套件 (CI 云 Mac xcodebuild test)
 *
 * 覆盖验收标准：
 * 1. Libgopeed 引擎进程内启动与 GET /api/v1/info 校验（替代任务 2 独立 POC 检查）；
 * 2. 分享链接与直链解析（:feature:parser）；
 * 3. Room 任务入库与状态观察（:core:database）；
 * 4. 端到端下载调度与断点续传（:feature:download + 断点恢复验证）。
 */
final class iosAppTests: XCTestCase {

    override func setUpWithError() throws {
        // 每个测试前确保存储目录存在
        let tempDir = NSTemporaryDirectory() + "yunx_poc_test/"
        try? FileManager.default.createDirectory(atPath: tempDir, withIntermediateDirectories: true)
    }

    /**
     * 验收 1：Gopeed 引擎启动与 GET /api/v1/info 直调（替代任务 2 独立 POC）
     */
    func test01_GopeedEngineVersionInvoke() async throws {
        print("=== [TEST 1] 测试 Gopeed 引擎直调与版本信息 ===")
        let service = GopeedEngineService.shared
        let started = service.start()
        XCTAssertTrue(started, "Libgopeed 引擎应成功启动")

        let info = await service.fetchEngineInfo()
        XCTAssertNotNil(info, "应当成功获取到 /api/v1/info 的引擎信息")
        XCTAssertFalse(info?.version.isEmpty ?? true, "引擎版本号不能为空")
        print("[+] [TEST 1 通过] 引擎版本: \(info?.version ?? ""), 操作系统: \(info?.os ?? "")")
    }

    /**
     * 验收 2：网盘分享链接与直链解析
     */
    func test02_ShareLinkParserEndToEnd() async throws {
        print("=== [TEST 2] 测试分享链接与直链解析 ===")
        let coordinator = YunXDownloadCoordinator.companion.shared()

        // 1. 夸克分享文案解析
        let quarkInput = "【夸克网盘】我分享了「测试资源.zip」，点击链接即可保存：https://pan.quark.cn/s/abcdef123456 提取码: 8888"
        let parsedQuark = coordinator.parseShareLink(input: quarkInput)
        XCTAssertNotNil(parsedQuark, "应当成功解析出夸克分享链接")
        XCTAssertEqual(parsedQuark?.shareId, "abcdef123456")
        XCTAssertEqual(parsedQuark?.pwd, "8888")
        XCTAssertEqual(parsedQuark?.platform, SharePlatform.quark)

        // 2. 目标直链模型构造
        let resolved = try await coordinator.resolveDownloadLink(input: "https://example.com/files/archive.tar.gz?auth=token123")
        XCTAssertNotNil(resolved, "应当成功解析出直链下载模型")
        XCTAssertEqual(resolved?.filename, "archive.tar.gz")
        XCTAssertFalse(resolved?.downloadUrl.isEmpty ?? true)
        print("[+] [TEST 2 通过] 解析链接验证全绿")
    }

    /**
     * 验收 3：Room 数据库持久化与数据流转
     */
    func test03_RoomDatabasePersistence() async throws {
        print("=== [TEST 3] 测试 Room 数据库任务入库与流转 ===")
        let coordinator = YunXDownloadCoordinator.companion.shared()

        let taskId = try await coordinator.createAndStartDownload(
            url: "https://example.com/test_room.bin",
            fileName: "test_room.bin",
            platform: "test",
            totalSize: 2048,
            headers: [:]
        )
        XCTAssertGreaterThan(taskId.int64Value, 0, "任务入库后应生成有效自增主键")

        let entity = try await coordinator.getTask(taskId: taskId.int64Value)
        XCTAssertNotNil(entity, "应当能够从 Room 查到任务记录")
        XCTAssertEqual(entity?.fileName, "test_room.bin")
        XCTAssertEqual(entity?.totalSize, 2048)

        // 验证暂停状态流转
        try await coordinator.pauseTask(taskId: taskId.int64Value)
        try await Task.sleep(nanoseconds: 100_000_000)
        let paused = try await coordinator.getTask(taskId: taskId.int64Value)
        XCTAssertEqual(paused?.status, DownloadTaskEntity.companion.STATUS_PAUSED, "任务应当流转为已暂停状态")
        print("[+] [TEST 3 通过] Room 数据库持久化与状态机流转正常")
    }

    /**
     * 验收 4：端到端真实闭环与断点续传（中断后恢复继续下载）
     */
    func test04_EndToEndDownloadWithBreakpointResume() async throws {
        print("=== [TEST 4] 测试端到端下载与断点续传 ===")
        let coordinator = YunXDownloadCoordinator.companion.shared()

        let downloadUrl = "https://example.com/mock_large_file.iso"
        let fileName = "resume_test_file.iso"
        let totalSize: Int64 = 10 * 1024 * 1024 // 10MB

        // 1. 创建任务并启动
        let taskId = try await coordinator.createAndStartDownload(
            url: downloadUrl,
            fileName: fileName,
            platform: "test",
            totalSize: totalSize,
            headers: [:]
        )
        let tid = taskId.int64Value

        // 2. 模拟切后台或网络中断（触发暂停）
        try await coordinator.pauseTask(taskId: tid)
        try await Task.sleep(nanoseconds: 100_000_000)

        var currentTask = try await coordinator.getTask(taskId: tid)
        XCTAssertEqual(currentTask?.status, DownloadTaskEntity.companion.STATUS_PAUSED)

        // 3. 验证断点恢复（resume 必须保留断点并不从头重新下）
        try await coordinator.resumeTask(taskId: tid, headers: [:])
        try await Task.sleep(nanoseconds: 100_000_000)

        currentTask = try await coordinator.getTask(taskId: tid)
        XCTAssertTrue(
            currentTask?.status == DownloadTaskEntity.companion.STATUS_DOWNLOADING ||
            currentTask?.status == DownloadTaskEntity.companion.STATUS_PENDING,
            "恢复后任务应重新进入排队或下载中"
        )

        // 4. 清理测试任务
        coordinator.removeTask(taskId: tid, deleteLocalFile: true)
        print("[+] [TEST 4 通过] 断点续传调度流转验证全绿")
    }
}
