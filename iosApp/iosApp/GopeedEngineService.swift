import Foundation
import Libgopeed
import SharedKit

/**
 * Gopeed 引擎信息响应结构体
 */
struct GopeedInfo: Decodable {
    let version: String
    let runtime: String?
    let os: String?
}

struct GopeedResponse<T: Decodable>: Decodable {
    let code: Int
    let msg: String?
    let data: T?
}

/**
 * Gopeed 原生直调单例服务 (iOS)
 * 封装 Libgopeed 进程内调用（apiEnable=false），不监听 TCP 端口
 */
final class GopeedEngineService: NSObject, LibgopeedInvokeResultListenerProtocol {
    static let shared = GopeedEngineService()

    private var reqCounter: Int64 = 2000
    private var pendingRequests = [Int64: (Bool, String?) -> Void]()
    private let lock = NSLock()

    private(set) var isStarted: Bool = false
    private(set) var engineVersion: String = "未启动"

    private override init() {
        super.init()
    }

    /**
     * 启动 Gopeed 引擎并注册到 KMP 的 GopeedBridge
     */
    func start() -> Bool {
        if isStarted { return true }

        let configJson = """
        {
            "apiEnable": false,
            "downloadConfig": {
                "connections": 16
            }
        }
        """

        var startError: NSError?
        var port: Int = 0
        let success = LibgopeedStart(configJson, &port, &startError)
        if !success || startError != nil {
            print("[-] [GopeedEngineService] LibgopeedStart 失败: \(startError?.localizedDescription ?? "unknown")")
            return false
        }

        isStarted = true
        print("[+] [GopeedEngineService] LibgopeedStart 成功 (apiEnable=false, 端口: \(port))")

        // 注册到 KMP 跨平台桥接
        GopeedBridge.shared.registerNativeInvoker { [weak self] method, path, query, body, requestId, callback in
            self?.invokeAsync(method: method, path: path, query: query, body: body) { success, payload in
                callback(requestId, KotlinBoolean(value: success), payload)
            }
        }

        return true
    }

    /**
     * 异步 invoke 封装（支持 async/await）
     */
    func invokeAsync(
        method: String,
        path: String,
        query: String = "",
        body: String = ""
    ) async -> (Bool, String?) {
        await withCheckedContinuation { continuation in
            invokeAsync(method: method, path: path, query: query, body: body) { success, payload in
                continuation.resume(returning: (success, payload))
            }
        }
    }

    func invokeAsync(
        method: String,
        path: String,
        query: String = "",
        body: String = "",
        completion: @escaping (Bool, String?) -> Void
    ) {
        lock.lock()
        reqCounter += 1
        let reqId = reqCounter
        pendingRequests[reqId] = completion
        lock.unlock()

        LibgopeedInvokeAsync(method, path, query, body, reqId, self)
    }

    /**
     * Libgopeed 回调实现
     */
    func onResult(_ requestID: Int64, success: Bool, payload: String?) {
        lock.lock()
        let handler = pendingRequests.removeValue(forKey: requestID)
        lock.unlock()

        handler?(success, payload)
    }

    /**
     * 调用 GET /api/v1/info 获取引擎信息（闭环替代任务 2 独立 POC）
     */
    func fetchEngineInfo() async -> GopeedInfo? {
        if !isStarted {
            _ = start()
        }

        let (success, payload) = await invokeAsync(method: "GET", path: "/api/v1/info")
        guard success, let payload = payload, let data = payload.data(using: .utf8) else {
            return nil
        }

        do {
            let resp = try JSONDecoder().decode(GopeedResponse<GopeedInfo>.self, from: data)
            if let info = resp.data {
                self.engineVersion = info.version
                return info
            }
        } catch {
            print("[-] [GopeedEngineService] 解析 /api/v1/info 失败: \(error)")
        }
        return nil
    }
}
