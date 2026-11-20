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

private enum DownloadTaskGroup: String, CaseIterable, Identifiable {
    case downloading
    case paused
    case completed
    case failed

    var id: String { rawValue }

    var title: String {
        switch self {
        case .downloading: return "下载中"
        case .paused: return "已暂停"
        case .completed: return "已完成"
        case .failed: return "失败"
        }
    }

    var icon: String {
        switch self {
        case .downloading: return "arrow.down.circle.fill"
        case .paused: return "pause.circle.fill"
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle.fill"
        }
    }

    var color: Color {
        switch self {
        case .downloading: return .blue
        case .paused: return .orange
        case .completed: return .green
        case .failed: return .red
        }
    }

    func contains(_ task: TaskUiModel) -> Bool {
        switch self {
        case .downloading: return task.isDownloading || task.isPending
        case .paused: return task.isPaused
        case .completed: return task.isCompleted
        case .failed: return task.isFailed
        }
    }
}

private struct DownloadsTabView: View {
    @ObservedObject var vm: DownloadViewModel
    @State private var isSelecting = false
    @State private var selectedTaskIds: Set<Int64> = []
    @State private var deleteCandidateIds: Set<Int64> = []

    private var groupedTasks: [(DownloadTaskGroup, [TaskUiModel])] {
        DownloadTaskGroup.allCases.compactMap { group in
            let matching = vm.tasks.filter(group.contains)
            return matching.isEmpty ? nil : (group, matching)
        }
    }

    private var activeCount: Int {
        vm.tasks.filter { $0.isDownloading || $0.isPending }.count
    }

    private var pausedCount: Int {
        vm.tasks.filter(\.isPaused).count
    }

    private var pausableSelection: Set<Int64> {
        Set(vm.tasks.filter {
            selectedTaskIds.contains($0.id) && ($0.isDownloading || $0.isPending)
        }.map(\.id))
    }

    private var resumableSelection: Set<Int64> {
        Set(vm.tasks.filter { selectedTaskIds.contains($0.id) && $0.isPaused }.map(\.id))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    summaryCard

                    if vm.tasks.isEmpty {
                        emptyState
                    } else {
                        ForEach(Array(groupedTasks.enumerated()), id: \.offset) { pair in
                            taskGroup(pair.element.0, tasks: pair.element.1)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("下载")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if isSelecting {
                        Button(selectedTaskIds.count == vm.tasks.count ? "取消全选" : "全选") {
                            if selectedTaskIds.count == vm.tasks.count {
                                selectedTaskIds.removeAll()
                            } else {
                                selectedTaskIds = Set(vm.tasks.map(\.id))
                            }
                        }
                        Button("完成") {
                            isSelecting = false
                            selectedTaskIds.removeAll()
                        }
                    } else {
                        NavigationLink {
                            EngineSettingsView(vm: vm)
                        } label: {
                            Image(systemName: "cpu")
                        }
                        .accessibilityLabel("引擎设置")

                        NavigationLink {
                            DownloadSettingsView(vm: vm)
                        } label: {
                            Image(systemName: "slider.horizontal.3")
                        }
                        .accessibilityLabel("下载设置")

                        Button("选择") {
                            isSelecting = true
                        }
                        .disabled(vm.tasks.isEmpty)
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if isSelecting {
                    batchActionBar
                }
            }
            .alert("删除下载任务？", isPresented: deleteAlertBinding) {
                Button("取消", role: .cancel) {
                    deleteCandidateIds.removeAll()
                }
                Button("删除", role: .destructive) {
                    let ids = deleteCandidateIds
                    deleteCandidateIds.removeAll()
                    vm.removeTasks(ids: ids)
                    selectedTaskIds.subtract(ids)
                    if selectedTaskIds.isEmpty {
                        isSelecting = false
                    }
                }
            } message: {
                Text("任务记录与已经下载的本地文件都会删除。")
            }
            .onChange(of: vm.tasks.map(\.id)) { ids in
                selectedTaskIds.formIntersection(Set(ids))
                if ids.isEmpty {
                    isSelecting = false
                }
            }
        }
    }

    private var deleteAlertBinding: Binding<Bool> {
        Binding(
            get: { !deleteCandidateIds.isEmpty },
            set: { if !$0 { deleteCandidateIds.removeAll() } }
        )
    }

    private var summaryCard: some View {
        HStack(spacing: 14) {
            summaryMetric(value: vm.tasks.count, title: "全部", color: .primary)
            Divider().frame(height: 30)
            summaryMetric(value: activeCount, title: "下载中", color: .blue)
            Divider().frame(height: 30)
            summaryMetric(value: pausedCount, title: "已暂停", color: .orange)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(14)
    }

    private func summaryMetric(value: Int, title: String, color: Color) -> some View {
        VStack(spacing: 3) {
            Text("\(value)")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(color)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray")
                .font(.system(size: 38))
                .foregroundColor(.secondary)
            Text("暂无下载任务")
                .font(.headline)
            Text("在「解析」Tab 解析链接并点击下载后，任务会按状态出现在这里。")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 34)
        .padding(.horizontal, 20)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(14)
    }

    private func taskGroup(_ group: DownloadTaskGroup, tasks: [TaskUiModel]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 7) {
                Image(systemName: group.icon)
                    .foregroundColor(group.color)
                Text(group.title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                Text("\(tasks.count)")
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(group.color.opacity(0.12))
                    .foregroundColor(group.color)
                    .cornerRadius(5)
                Spacer()
            }

            ForEach(tasks) { task in
                taskCard(task)
            }
        }
    }

    private func taskCard(_ task: TaskUiModel) -> some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack(alignment: .top, spacing: 10) {
                if isSelecting {
                    Image(systemName: selectedTaskIds.contains(task.id) ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundColor(selectedTaskIds.contains(task.id) ? .blue : .secondary)
                        .padding(.top, 2)
                }

                ZStack {
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(progressTint(for: task).opacity(0.12))
                        .frame(width: 38, height: 38)
                    Image(systemName: task.isFailed ? "exclamationmark.arrow.triangle.2.circlepath" : "doc.fill")
                        .font(.subheadline)
                        .foregroundColor(progressTint(for: task))
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(task.fileName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .lineLimit(2)

                    HStack(spacing: 7) {
                        Text(task.platformText)
                        Text("ID #\(task.id)")
                    }
                    .font(.caption2)
                    .foregroundColor(.secondary)
                }

                Spacer(minLength: 4)
                statusBadge(task: task)
            }

            ProgressView(value: task.progress)
                .tint(progressTint(for: task))

            HStack {
                Text("\(task.downloadedFormatted) / \(task.totalFormatted)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text(String(format: "%.1f%%", task.progress * 100))
                    .font(.caption)
                    .fontWeight(.semibold)
            }

            if task.isDownloading {
                HStack(spacing: 12) {
                    if let speed = vm.speedText(for: task) {
                        Label(speed, systemImage: "speedometer")
                    }
                    if let remaining = vm.remainingTimeText(for: task) {
                        Label(remaining, systemImage: "clock")
                    }
                    Spacer()
                }
                .font(.caption2)
                .foregroundColor(.secondary)
            }

            if !isSelecting {
                Divider()
                HStack(spacing: 10) {
                    Spacer()

                    if task.isDownloading || task.isPending {
                        actionButton("暂停", icon: "pause.fill", color: .orange) {
                            vm.pauseTask(id: task.id)
                        }
                    }
                    if task.isPaused {
                        actionButton("继续", icon: "play.fill", color: .green) {
                            vm.resumeTask(id: task.id)
                        }
                    }
                    if task.isFailed {
                        actionButton("重试", icon: "arrow.clockwise", color: .blue) {
                            vm.retryTask(id: task.id)
                        }
                    }
                    Button {
                        deleteCandidateIds = [task.id]
                    } label: {
                        Image(systemName: "trash")
                            .font(.caption)
                            .padding(7)
                            .background(Color.red.opacity(0.1))
                            .foregroundColor(.red)
                            .cornerRadius(7)
                    }
                    .accessibilityLabel("删除任务")
                }
            }
        }
        .padding(13)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    isSelecting && selectedTaskIds.contains(task.id) ? Color.blue.opacity(0.55) : Color.clear,
                    lineWidth: 1
                )
        )
        .contentShape(Rectangle())
        .onTapGesture {
            guard isSelecting else { return }
            if selectedTaskIds.contains(task.id) {
                selectedTaskIds.remove(task.id)
            } else {
                selectedTaskIds.insert(task.id)
            }
        }
    }

    private var batchActionBar: some View {
        VStack(spacing: 9) {
            HStack {
                Text(selectedTaskIds.isEmpty ? "请选择任务" : "已选 \(selectedTaskIds.count) 项")
                    .font(.caption)
                    .fontWeight(.semibold)
                Spacer()
            }

            HStack(spacing: 9) {
                batchButton("批量暂停", icon: "pause.fill", color: .orange, enabled: !pausableSelection.isEmpty) {
                    vm.pauseTasks(ids: selectedTaskIds)
                }
                batchButton("批量恢复", icon: "play.fill", color: .green, enabled: !resumableSelection.isEmpty) {
                    vm.resumeTasks(ids: selectedTaskIds)
                }
                batchButton("批量删除", icon: "trash", color: .red, enabled: !selectedTaskIds.isEmpty) {
                    deleteCandidateIds = selectedTaskIds
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Divider()
        }
    }

    private func batchButton(
        _ title: String,
        icon: String,
        color: Color,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.caption)
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(enabled ? color.opacity(0.12) : Color.gray.opacity(0.08))
                .foregroundColor(enabled ? color : .secondary)
                .cornerRadius(9)
        }
        .disabled(!enabled)
    }

    private func actionButton(
        _ title: String,
        icon: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.caption)
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(color.opacity(0.12))
                .foregroundColor(color)
                .cornerRadius(7)
        }
    }

    private func progressTint(for task: TaskUiModel) -> Color {
        if task.isCompleted { return .green }
        if task.isPaused { return .orange }
        if task.isFailed { return .red }
        return .blue
    }

    private func statusBadge(task: TaskUiModel) -> some View {
        let color = progressTint(for: task)
        return Text(task.statusText)
            .font(.caption2)
            .fontWeight(.semibold)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .foregroundColor(color)
            .cornerRadius(5)
    }
}

private struct DownloadSettingsView: View {
    @ObservedObject var vm: DownloadViewModel

    private let concurrencyOptions: [Int32] = [1, 2, 3, 5, 8, 10]

    var body: some View {
        Form {
            Section("任务调度") {
                Picker(
                    "最多同时下载任务数",
                    selection: Binding(
                        get: { vm.maxConcurrentDownloads },
                        set: { vm.setMaxConcurrentDownloads($0) }
                    )
                ) {
                    ForEach(concurrencyOptions, id: \.self) { value in
                        Text("\(value) 个任务").tag(value)
                    }
                }

                Text("超出的新任务会进入排队；修改后立即保存到 shared 设置存储。")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("保存位置") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("当前下载目录")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(vm.downloadDirectory)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                }
                .padding(.vertical, 3)

                Text("iOS 下载目录位于 App 沙盒内。目录选择与 Files 导出将在任务 10.7 接入。")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("引擎") {
                NavigationLink {
                    EngineSettingsView(vm: vm)
                } label: {
                    HStack {
                        Label("下载引擎", systemImage: "cpu")
                        Spacer()
                        Text(vm.downloadEngine == "builtin" ? "内置分片" : "Gopeed")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Section("持久化") {
                Text("任务调度与引擎偏好由 shared 设置存储持久化，重启 App 后仍会保留。")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .navigationTitle("下载设置")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct EngineSettingsView: View {
    @ObservedObject var vm: DownloadViewModel

    var body: some View {
        Form {
            Section("引擎选择") {
                Picker(
                    "下载引擎",
                    selection: Binding(
                        get: { vm.downloadEngine },
                        set: { vm.setDownloadEngine($0) }
                    )
                ) {
                    Text("Gopeed 进程内引擎").tag("gopeed")
                    Text("内置分片调度").tag("builtin")
                }

                Text("Gopeed 负责磁力等多协议引擎任务；普通 HTTP/HLS 下载由内置分片调度处理。")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if vm.downloadEngine == "builtin" {
                    Label("内置分片不支持磁力链接；磁力任务需要切换到 Gopeed。", systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
            }

            Section("运行状态") {
                HStack(spacing: 9) {
                    Circle()
                        .fill(vm.isEngineReady ? Color.green : Color.orange)
                        .frame(width: 9, height: 9)
                    Text(vm.engineVersion)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }

                LabeledContent("运行方式", value: "进程内直调")
                LabeledContent("KMP 核心", value: vm.kmpVersion)
            }

            Section("切换说明") {
                Text("切换只影响之后新建的引擎任务；已有任务的执行状态与断点不会被重置。")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button {
                    vm.startEngineAndLoad()
                } label: {
                    Label("重新检测引擎", systemImage: "arrow.clockwise")
                }
            }
        }
        .navigationTitle("引擎设置")
        .navigationBarTitleDisplayMode(.inline)
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
