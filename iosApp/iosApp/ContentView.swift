import SwiftUI
import UIKit
import SharedKit

private enum AppTab: String, CaseIterable, Identifiable {
    case resolve
    case account
    case downloads
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .resolve: return "解析"
        case .account: return "账号"
        case .downloads: return "下载"
        case .settings: return "设置"
        }
    }

    var systemImage: String {
        switch self {
        case .resolve: return "link"
        case .account: return "person.crop.circle"
        case .downloads: return "arrow.down.circle"
        case .settings: return "gearshape"
        }
    }
}

private enum AppAppearance: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

private enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case simplifiedChinese
    case english

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "跟随系统"
        case .simplifiedChinese: return "简体中文"
        case .english: return "English"
        }
    }

    var locale: Locale {
        switch self {
        case .system: return .autoupdatingCurrent
        case .simplifiedChinese: return Locale(identifier: "zh-Hans")
        case .english: return Locale(identifier: "en")
        }
    }
}

struct ContentView: View {
    @StateObject private var vm = DownloadViewModel()
    @AppStorage("yunx.appearance") private var appearanceRaw = AppAppearance.system.rawValue
    @AppStorage("yunx.language") private var languageRaw = AppLanguage.system.rawValue
    @State private var selectedTab: AppTab = .resolve

    private var appearance: AppAppearance {
        AppAppearance(rawValue: appearanceRaw) ?? .system
    }

    private var language: AppLanguage {
        AppLanguage(rawValue: languageRaw) ?? .system
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            ResolveTabView(vm: vm)
                .tabItem {
                    Label(AppTab.resolve.title, systemImage: AppTab.resolve.systemImage)
                }
                .tag(AppTab.resolve)

            AccountTabView()
                .tabItem {
                    Label(AppTab.account.title, systemImage: AppTab.account.systemImage)
                }
                .tag(AppTab.account)

            DownloadsTabView(vm: vm)
                .tabItem {
                    Label(AppTab.downloads.title, systemImage: AppTab.downloads.systemImage)
                }
                .badge(vm.tasks.count)
                .tag(AppTab.downloads)

            SettingsTabView(vm: vm)
                .tabItem {
                    Label(AppTab.settings.title, systemImage: AppTab.settings.systemImage)
                }
                .tag(AppTab.settings)
        }
        .tint(.blue)
        .preferredColorScheme(appearance.colorScheme)
        .environment(\.locale, language.locale)
    }
}

private struct ResolveTabView: View {
    @ObservedObject var vm: DownloadViewModel
    @AppStorage("yunx.hasSeenWelcome") private var hasSeenWelcome = false
    @State private var showsTransferSheet = false
    @FocusState private var isLinkInputFocused: Bool

    private var selectedCount: Int {
        vm.resolvedFiles.filter(\.isSelected).count
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if !hasSeenWelcome {
                        FirstLaunchGuideCard {
                            withAnimation {
                                hasSeenWelcome = true
                            }
                        }
                    }

                    resolveHeroCard
                    linkInputCard

                    if let message = vm.parseErrorMessage {
                        ResolveErrorBanner(message: message)
                    }

                    if vm.isParsing {
                        parsingCard
                    }

                    if let share = vm.resolvedShare {
                        ShareSummaryCard(share: share)
                    }

                    if !vm.resolvedFiles.isEmpty {
                        fileListCard
                    }

                    resolveHintCard
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("解析")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { showsTransferSheet = true }) {
                        Label("转存", systemImage: "folder.badge.plus")
                    }
                    .accessibilityLabel("转存网盘")
                }
            }
            .sheet(isPresented: $showsTransferSheet) {
                TransferToCloudSheet(share: vm.resolvedShare, files: vm.resolvedFiles)
            }
        }
    }

    private var resolveHeroCard: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(0.18))
                    .frame(width: 52, height: 52)
                Image(systemName: "link.badge.plus")
                    .font(.title2)
                    .foregroundColor(.white)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("粘贴分享链接，直达下载")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                Text("支持夸克、百度、123、迅雷、蓝奏等网盘分享文案，也支持 HTTP/HTTPS 直链。")
                    .font(.footnote)
                    .foregroundColor(.white.opacity(0.88))
            }

            Spacer(minLength: 0)
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [Color.blue, Color.indigo],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
    }

    private var linkInputCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("分享链接")
                        .font(.headline)
                    Text("粘贴整段分享文案，系统会自动提取链接与提取码。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Button(action: pasteFromClipboard) {
                    Label("粘贴", systemImage: "doc.on.clipboard")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color.blue.opacity(0.12))
                        .foregroundColor(.blue)
                        .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }

            TextField("粘贴网盘分享链接或 HTTP 直链...", text: $vm.inputLink, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(3...6)
                .focused($isLinkInputFocused)
                .padding(12)
                .background(Color(.tertiarySystemGroupedBackground))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isLinkInputFocused ? Color.blue.opacity(0.55) : Color.clear, lineWidth: 1)
                )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    quickFillTag(title: "蓝奏云示例", content: "【蓝奏云】inphic.game.mouse.pw1.rar 免密分享：https://wwr.lanzoui.com/iQcpNspli5c")
                    quickFillTag(title: "10MB 测试直链", content: "https://speed.cloudflare.com/__down?bytes=10485760")
                    quickFillTag(title: "夸克分享示例", content: "夸克网盘：https://pan.quark.cn/s/abcdef123456 提取码: 8888")
                }
            }

            Button(action: {
                isLinkInputFocused = false
                vm.resolveShare()
            }) {
                HStack {
                    Spacer()
                    if vm.isParsing {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Image(systemName: "magnifyingglass")
                    }
                    Text(vm.isParsing ? "正在解析..." : "解析分享")
                        .fontWeight(.semibold)
                    Spacer()
                }
                .padding(.vertical, 10)
                .background(canResolve ? Color.blue : Color.blue.opacity(0.35))
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            .disabled(!canResolve)
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private var canResolve: Bool {
        !vm.isParsing && !vm.inputLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var parsingCard: some View {
        HStack(spacing: 12) {
            ProgressView()
                .tint(.blue)
            VStack(alignment: .leading, spacing: 3) {
                Text("正在解析分享链接")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Text("正在请求网盘接口，请稍候…")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private var fileListCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("文件列表")
                        .font(.headline)
                    Text("已选 \(selectedCount) / \(vm.resolvedFiles.count)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Button(selectedCount == vm.resolvedFiles.count ? "取消全选" : "全选") {
                    if selectedCount == vm.resolvedFiles.count {
                        vm.clearFileSelection()
                    } else {
                        vm.selectAllFiles()
                    }
                }
                .font(.caption)
                .fontWeight(.semibold)
            }

            ForEach(Array(vm.resolvedFiles.enumerated()), id: \.element.id) { index, file in
                ResolvedFileRow(
                    file: file,
                    task: vm.task(for: file),
                    isStarting: vm.startingDownloadIds.contains(file.id),
                    onToggle: { vm.toggleFileSelection(id: file.id) },
                    onDownload: { vm.download(fileId: file.id) }
                )

                if index < vm.resolvedFiles.count - 1 {
                    Divider()
                }
            }

            HStack(spacing: 10) {
                Button(action: { vm.downloadSelectedFiles() }) {
                    Label("下载选中", systemImage: "arrow.down.to.line")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(selectedCount > 0 ? Color.blue : Color.blue.opacity(0.35))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .disabled(selectedCount == 0)

                Button(action: { vm.downloadAllFiles() }) {
                    Label("全部下载", systemImage: "arrow.down.circle.fill")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.blue.opacity(0.12))
                        .foregroundColor(.blue)
                        .cornerRadius(10)
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private var resolveHintCard: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "arrow.turn.down.right")
                .foregroundColor(.blue)
            Text("解析只生成文件清单，不会自动下载。点击下载后任务会进入「下载」Tab，进度会在这里和下载页同步更新。")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }

    private func pasteFromClipboard() {
        guard let text = UIPasteboard.general.string, !text.isEmpty else {
            vm.parseErrorMessage = "剪贴板为空，请先复制一条分享链接。"
            return
        }
        vm.inputLink = text
        vm.parseErrorMessage = nil
    }

    private func quickFillTag(title: String, content: String) -> some View {
        Button(action: {
            vm.inputLink = content
            vm.parseErrorMessage = nil
        }) {
            Text(title)
                .font(.caption2)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.blue.opacity(0.1))
                .foregroundColor(.blue)
                .cornerRadius(6)
        }
    }
}

private struct ResolveErrorBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)
            VStack(alignment: .leading, spacing: 3) {
                Text("操作未完成")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.red)
                Text(message)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color.red.opacity(0.08))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.red.opacity(0.18), lineWidth: 1)
        )
    }
}

private struct ShareSummaryCard: View {
    let share: ResolvedShareUiModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("分享详情")
                .font(.headline)

            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.blue.opacity(0.12))
                        .frame(width: 44, height: 44)
                    Image(systemName: "doc.fill")
                        .font(.title3)
                        .foregroundColor(.blue)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(share.title)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        Text(share.platformName)
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.blue.opacity(0.12))
                            .foregroundColor(.blue)
                            .cornerRadius(5)

                        Label(share.sizeText, systemImage: "internaldrive")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer(minLength: 0)
            }

            Divider()

            HStack {
                Text("平台")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text(share.platformName)
                    .font(.caption)
                    .fontWeight(.medium)
            }

            HStack {
                Text("大小")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text(share.sizeText)
                    .font(.caption)
                    .fontWeight(.medium)
            }

            HStack {
                Text("来源")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text(share.detail)
                    .font(.caption)
                    .fontWeight(.medium)
                    .lineLimit(1)
            }

            if let warning = share.warning {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .foregroundColor(.orange)
                    Text(warning)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(10)
                .background(Color.orange.opacity(0.08))
                .cornerRadius(8)
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

private struct ResolvedFileRow: View {
    let file: ResolvedFileUiModel
    let task: TaskUiModel?
    let isStarting: Bool
    let onToggle: () -> Void
    let onDownload: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Button(action: onToggle) {
                    Image(systemName: file.isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundColor(file.isSelected ? .blue : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(file.isSelected ? "取消勾选" : "勾选文件")

                VStack(alignment: .leading, spacing: 4) {
                    Text(file.fileName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        Label(file.sizeText, systemImage: "internaldrive")
                        Text(file.platformName)
                    }
                    .font(.caption2)
                    .foregroundColor(.secondary)
                }

                Spacer(minLength: 0)

                if isStarting {
                    ProgressView()
                        .frame(width: 70)
                } else if let task {
                    Text(task.statusText)
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundColor(statusColor(for: task))
                } else if file.taskId != nil {
                    Text("已加入下载")
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundColor(.blue)
                } else {
                    Button(action: onDownload) {
                        Label("下载", systemImage: "arrow.down")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.blue.opacity(0.12))
                            .foregroundColor(.blue)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }

            if let task {
                VStack(spacing: 5) {
                    ProgressView(value: task.progress)
                        .tint(task.isCompleted ? .green : (task.isFailed ? .red : (task.isPaused ? .orange : .blue)))

                    HStack {
                        Text("\(task.downloadedFormatted) / \(task.totalFormatted)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(String(format: "%.1f%%", task.progress * 100))
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }

    private func statusColor(for task: TaskUiModel) -> Color {
        if task.isCompleted { return .green }
        if task.isFailed { return .red }
        if task.isPaused { return .orange }
        if task.isDownloading { return .blue }
        return .secondary
    }
}

private struct TransferToCloudSheet: View {
    @Environment(\.dismiss) private var dismiss
    let share: ResolvedShareUiModel?
    let files: [ResolvedFileUiModel]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    PlaceholderPanel(
                        icon: "folder.badge.plus",
                        title: "转存到网盘",
                        taskLabel: "任务 10.5 做",
                        description: "转存入口已接通。网盘选择、目标目录与转存接口将在任务 10.5 完成。"
                    )

                    if let share {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("本次分享")
                                .font(.headline)
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(share.title)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .lineLimit(2)
                                    Text("\(share.platformName) · \(share.sizeText)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Text("\(files.count) 个文件")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(12)
                    } else {
                        Text("请先在解析页粘贴并解析一条分享链接。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(14)
                            .background(Color(.secondarySystemGroupedBackground))
                            .cornerRadius(12)
                    }

                    Button(action: {}) {
                        Label("选择转存网盘（10.5 做）", systemImage: "externaldrive.badge.plus")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Color.gray.opacity(0.15))
                            .foregroundColor(.secondary)
                            .cornerRadius(10)
                    }
                    .disabled(true)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("转存网盘")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") {
                        dismiss()
                    }
                }
            }
        }
    }
}

private struct FirstLaunchGuideCard: View {
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "sparkles")
                    .font(.title2)
                Text("欢迎使用云析")
                    .font(.title3)
                    .fontWeight(.bold)
            }

            Text("解析分享链接、管理下载任务。账号与完整设置页会在后续任务中逐步开放。")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))

            Button(action: onDismiss) {
                Text("开始使用")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Color.white)
                    .foregroundColor(.blue)
                    .cornerRadius(9)
            }
        }
        .foregroundColor(.white)
        .padding(16)
        .background(
            LinearGradient(
                colors: [Color.blue, Color.indigo],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
    }
}

private struct AccountTabView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    PlaceholderPanel(
                        icon: "person.crop.circle.badge.checkmark",
                        title: "账号管理",
                        taskLabel: "任务 10.4 做",
                        description: "网盘账号列表、WKWebView 登录、Cookie 持久化与登录状态展示将在任务 10.4 接入。"
                    )

                    VStack(spacing: 0) {
                        plannedRow(icon: "list.bullet.rectangle", title: "10 个网盘平台账号")
                        Divider()
                        plannedRow(icon: "safari", title: "各平台 WKWebView 登录")
                        Divider()
                        plannedRow(icon: "key.fill", title: "Cookie 回传与安全持久化")
                    }
                    .background(Color(.secondarySystemGroupedBackground))
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("账号")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func plannedRow(icon: String, title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .frame(width: 22)
                .foregroundColor(.blue)
            Text(title)
                .font(.subheadline)
            Spacer()
        }
        .padding(14)
    }
}

private struct DownloadsTabView: View {
    @ObservedObject var vm: DownloadViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("下载任务 (\(vm.tasks.count))")
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
                            Text("在「解析」Tab 添加链接后，任务会出现在这里。")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 28)
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(12)
                    } else {
                        ForEach(vm.tasks) { task in
                            taskCard(task: task)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("下载")
            .navigationBarTitleDisplayMode(.inline)
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
}

private struct SettingsTabView: View {
    @ObservedObject var vm: DownloadViewModel
    @AppStorage("yunx.appearance") private var appearanceRaw = AppAppearance.system.rawValue
    @AppStorage("yunx.language") private var languageRaw = AppLanguage.system.rawValue

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Label("设置中心", systemImage: "gearshape.fill")
                                .font(.headline)
                            Spacer()
                            PlaceholderTag(text: "任务 10.6 做", color: .orange)
                        }
                        Text("关于、公告、更新检查、书签与支持页将在任务 10.6 完成。本轮先提供主题、语言与调试入口。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }

                Section("外观") {
                    Picker("主题", selection: $appearanceRaw) {
                        ForEach(AppAppearance.allCases) { appearance in
                            Text(appearance.title).tag(appearance.rawValue)
                        }
                    }
                    Text("主题选择会立即生效并持久化；更多外观选项在 10.6 补齐。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section("语言") {
                    Picker("界面语言", selection: $languageRaw) {
                        ForEach(AppLanguage.allCases) { language in
                            Text(language.title).tag(language.rawValue)
                        }
                    }
                    Text("语言偏好已持久化，本地化文案将在 10.6 接入。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section("开发者") {
                    NavigationLink {
                        DebugView(vm: vm)
                    } label: {
                        HStack {
                            Label("调试与引擎", systemImage: "wrench.and.screwdriver.fill")
                            Spacer()
                            Text(vm.isEngineReady ? "引擎就绪" : "初始化中")
                                .font(.caption)
                                .foregroundColor(vm.isEngineReady ? .green : .orange)
                        }
                    }
                }

                Section("关于") {
                    LabeledContent("应用", value: "云析")
                    LabeledContent("版本", value: appVersion)
                }
            }
            .navigationTitle("设置")
        }
    }
}

private struct PlaceholderPanel: View {
    let icon: String
    let title: String
    let taskLabel: String
    let description: String

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 42))
                .foregroundColor(.blue)

            HStack(spacing: 8) {
                Text(title)
                    .font(.title3)
                    .fontWeight(.bold)
                PlaceholderTag(text: taskLabel, color: .orange)
            }

            Text(description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 30)
        .padding(.horizontal, 20)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

private struct PlaceholderTag: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2)
            .fontWeight(.semibold)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .cornerRadius(4)
    }
}

private struct DebugView: View {
    @ObservedObject var vm: DownloadViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                engineStatusCard
                e2ePocBanner
                logConsoleSection
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("调试与引擎")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { vm.startEngineAndLoad() }) {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
    }

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
                LinearGradient(
                    colors: [Color.blue, Color.indigo],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(12)
            .shadow(color: Color.blue.opacity(0.3), radius: 6, x: 0, y: 3)
        }
        .disabled(vm.isProcessing)
    }

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
                .frame(height: 180)
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
