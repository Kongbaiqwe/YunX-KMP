import SwiftUI
import SharedKit

struct ContentView: View {
    @StateObject private var vm = DownloadViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 1. 顶部引擎与架构状态卡片
                    engineStatusCard

                    // 2. 自动化端到端 POC 运行横幅
                    e2ePocBanner

                    // 3. 链接输入与智能解析卡片
                    linkInputCard

                    // 4. 下载任务列表
                    taskListSection

                    // 5. 实时操作与断点调试日志控制台
                    logConsoleSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("云析")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { vm.startEngineAndLoad() }) {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
    }

    // MARK: - 1. 顶部引擎与架构状态卡片
    private var engineStatusCard: some View {
        VStack(spacing: 10) {
            HStack {
                HStack(spacing: 6) {
                    Circle()
                        .fill(vm.isEngineReady ? Color.green : Color.orange)
                        .frame(width: 10, height: 10)
                    Text(vm.engineVersion)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
                Spacer()
                Text("In-Process")
                    .font(.caption2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.blue.opacity(0.15))
                    .foregroundColor(.blue)
                    .cornerRadius(4)
            }

            Divider()

            HStack {
                Label("KMP 核心: \(vm.kmpVersion)", systemImage: "cube.fill")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Label("Room KMP + Bundled SQLite", systemImage: "cylinder.split.1x2.fill")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    // MARK: - 2. 自动化端到端 POC 运行横幅
    private var e2ePocBanner: some View {
        Button(action: { vm.runAutomatedE2EPoc() }) {
            HStack(spacing: 12) {
                Image(systemName: "bolt.horizontal.circle.fill")
                    .resizable()
                    .frame(width: 32, height: 32)
                    .foregroundColor(.white)

                VStack(alignment: .leading, spacing: 2) {
                    Text("运行端到端 POC 闭环验证")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("测试：引擎信息 -> 链接解析 -> 入库 -> 断点暂停 -> 恢复续传")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.85))
                }

                Spacer()

                if vm.isProcessing {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: "play.circle.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                }
            }
            .padding(14)
            .background(
                LinearGradient(colors: [Color.blue, Color.indigo], startPoint: .leading, endPoint: .trailing)
            )
            .cornerRadius(12)
            .shadow(color: Color.blue.opacity(0.3), radius: 6, x: 0, y: 3)
        }
        .disabled(vm.isProcessing)
    }

    // MARK: - 3. 链接输入与智能解析卡片
    private var linkInputCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("网盘分享链接 / 直接下载直链")
                .font(.subheadline)
                .fontWeight(.bold)

            TextField("粘贴夸克/百度/123/迅雷分享链接或 HTTP 直链...", text: $vm.inputLink, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(2...4)
                .padding(10)
                .background(Color(.tertiarySystemGroupedBackground))
                .cornerRadius(8)

            // 预设快捷测试标签
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    quickFillTag(title: "夸克分享", content: "夸克网盘：https://pan.quark.cn/s/abcdef123456 提取码: 8888")
                    quickFillTag(title: "10MB测试直链", content: "https://speed.cloudflare.com/__down?bytes=10485760")
                    quickFillTag(title: "123云盘", content: "https://www.123pan.com/s/123456-abcde 提取码: 1234")
                }
            }

            Button(action: { vm.parseAndDownload() }) {
                HStack {
                    Spacer()
                    Image(systemName: "arrow.down.doc.fill")
                    Text("开始解析并下载")
                        .fontWeight(.semibold)
                    Spacer()
                }
                .padding(.vertical, 10)
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            .disabled(vm.isProcessing || vm.inputLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private func quickFillTag(title: String, content: String) -> some View {
        Button(action: { vm.inputLink = content }) {
            Text(title)
                .font(.caption2)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.blue.opacity(0.1))
                .foregroundColor(.blue)
                .cornerRadius(6)
        }
    }

    // MARK: - 4. 下载任务列表
    private var taskListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("下载调度任务 (\(vm.tasks.count))")
                    .font(.subheadline)
                    .fontWeight(.bold)
                Spacer()
                Text("Room 持久化")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }

            if vm.tasks.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "tray")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("暂无下载任务")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .background(Color(.secondarySystemGroupedBackground))
                .cornerRadius(12)
            } else {
                ForEach(vm.tasks) { task in
                    taskCard(task: task)
                }
            }
        }
    }

    private func taskCard(task: TaskUiModel) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "doc.fill")
                    .foregroundColor(.blue)
                Text(task.fileName)
                    .font(.headline)
                    .lineLimit(1)
                Spacer()
                statusBadge(task: task)
            }

            // 进度条
            ProgressView(value: task.progress)
                .tint(task.isCompleted ? .green : (task.isPaused ? .orange : .blue))

            HStack {
                Text("\(task.downloadedFormatted) / \(task.totalFormatted)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text(String(format: "%.1f%%", task.progress * 100))
                    .font(.caption)
                    .fontWeight(.semibold)
            }

            Divider()

            HStack(spacing: 12) {
                Text("ID: #\(task.id)")
                    .font(.caption2)
                    .foregroundColor(.secondary)

                Spacer()

                if task.isDownloading {
                    Button(action: { vm.pauseTask(id: task.id) }) {
                        Label("暂停", systemImage: "pause.fill")
                            .font(.caption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.orange.opacity(0.15))
                            .foregroundColor(.orange)
                            .cornerRadius(6)
                    }
                } else if task.isPaused {
                    Button(action: { vm.resumeTask(id: task.id) }) {
                        Label("继续 (断点续传)", systemImage: "play.fill")
                            .font(.caption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.green.opacity(0.15))
                            .foregroundColor(.green)
                            .cornerRadius(6)
                    }
                }

                Button(action: { vm.removeTask(id: task.id) }) {
                    Image(systemName: "trash")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private func statusBadge(task: TaskUiModel) -> some View {
        let color: Color
        if task.isCompleted { color = .green }
        else if task.isDownloading { color = .blue }
        else if task.isPaused { color = .orange }
        else if task.isFailed { color = .red }
        else { color = .gray }

        return Text(task.statusText)
            .font(.caption2)
            .fontWeight(.semibold)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(4)
    }

    // MARK: - 5. 实时操作与断点调试日志控制台
    private var logConsoleSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("执行与断点调试轨迹", systemImage: "terminal.fill")
                    .font(.subheadline)
                    .fontWeight(.bold)
                Spacer()
                Button("清空") {
                    vm.logs.removeAll()
                }
                .font(.caption)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        if vm.logs.isEmpty {
                            Text("// 待命就绪，操作日志将在此实时滚动...")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(Array(vm.logs.enumerated()), id: \.offset) { index, log in
                                Text(log)
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundColor(log.contains("[-] ") ? .red : (log.contains("[+] ") ? .green : .white))
                                    .id(index)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                }
                .frame(height: 140)
                .background(Color.black.opacity(0.85))
                .cornerRadius(8)
                .onChange(of: vm.logs.count) { _ in
                    if let last = vm.logs.indices.last {
                        proxy.scrollTo(last, anchor: .bottom)
                    }
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}
