import Foundation
import Libgopeed

/**
 * Gopeed iOS 原生直调验证 POC (Task 2) —— 可断言版
 *
 * 验证目标：
 * 1. 验证 Libgopeed.xcframework 能否被 Swift 正确链接；
 * 2. 验证进程内调用（apiEnable = false），不监听任何本地 TCP 端口；
 * 3. 验证通过 invoke 接口成功获取引擎信息（GET /api/v1/info）。
 *
 * 与原版（d:\A\P5\YunX-KMP\poc-gopeed\TestInvoke.swift）的区别：
 * 1. 端口改了：原版调 GET /api/v1/version，但 main 分支的路由表里根本没有
 *    /api/v1/version（pkg/api/service.go 只有 /api/v1/info），调它只会拿到
 *    {"code":1000,"msg":"not found"}。改成 GET /api/v1/info，返回
 *    {"code":0,"data":{"version":"...","runtime":"...","os":"ios",...}}。
 * 2. runPoc() 现在返回 Bool：true = 引擎启动成功且 invoke 回调 success 且拿到
 *    非空响应；false = 启动失败 / 5 秒超时 / 回调 success 为 false / 响应为空。
 * 3. lastResult 改为 private(set)，新增 private(set) var lastSuccess：测试可以
 *    读到回调结果做断言，但外部仍不能改。其他逻辑（启动配置、信号量等待、打印）
 *    与原版完全一致。
 */
class GopeedPocTester: NSObject, LibgopeedInvokeResultListenerProtocol {

    private let semaphore = DispatchSemaphore(value: 0)
    private(set) var lastResult: String = ""
    private(set) var lastSuccess: Bool = false

    @discardableResult
    func runPoc() -> Bool {
        print("=== [POC] 启动 Gopeed iOS 直调验证 ===")

        // 1. 启动引擎（配置 apiEnable=false，纯进程内调用）
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
            print("[-] LibgopeedStart 失败: \(startError?.localizedDescription ?? "unknown error")")
            return false
        }
        print("[+] LibgopeedStart 成功 (进程内模式, 端口: \(port))")

        // 2. 调用 invoke 接口获取引擎信息
        // 注意：main 分支没有 /api/v1/version，只有 /api/v1/info
        let requestId = Int64(1001)
        LibgopeedInvokeAsync(
            "GET",
            "/api/v1/info",
            "",
            "",
            requestId,
            self
        )

        // 等待异步回调（最多等待 5 秒）
        let waitResult = semaphore.wait(timeout: .now() + 5.0)
        if waitResult == .timedOut {
            print("[-] Invoke 调用超时")
            return false
        }
        print("[+] Invoke 调用成功验证闭环！响应: \(lastResult)")
        return lastSuccess && !lastResult.isEmpty
    }

    // Libgopeed 回调协议实现
    // 头文件签名：- (void)onResult:(int64_t)requestID success:(BOOL)success payload:(NSString*)payload
    func onResult(_ requestID: Int64, success: Bool, payload: String?) {
        print("[*] 收到引擎异步回调: reqId=\(requestID), success=\(success)")
        self.lastSuccess = success
        if let payload = payload {
            self.lastResult = payload
        }
        semaphore.signal()
    }
}
