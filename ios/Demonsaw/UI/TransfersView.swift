import SwiftUI

// Port of TransfersPane() + TransferList()
struct TransfersPane: View {
    let snapshot: Snapshot
    let vm: AppViewModel

    @State private var section = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $section) {
                Text("Downloads").tag(0)
                Text("Uploads").tag(1)
                Text("Finished").tag(2)
                Text("Log").tag(3)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            switch section {
            case 0:
                transferList(
                    snapshot.downloads,
                    empty: "No downloads queued.\nBrowse a peer and tap a file.",
                    onCancel: { vm.cancelDownload($0) }
                )
            case 1:
                transferList(
                    snapshot.uploads,
                    empty: "No uploads yet.\nUploads happen when peers download your shares.",
                    onCancel: { vm.cancelUpload($0) }
                )
            case 2:
                finishedList
            default:
                logList
            }
        }
        .background(DsTheme.background)
    }

    @ViewBuilder
    private func transferList(_ transfers: [Transfer], empty: String, onCancel: @escaping (String) -> Void) -> some View {
        if transfers.isEmpty {
            Text(empty)
                .multilineTextAlignment(.center)
                .foregroundColor(DsTheme.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(transfers) { transfer in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: transfer.done ? "doc" : "icloud.and.arrow.down")
                                    .foregroundColor(transfer.done ? DsTheme.green : DsTheme.blue)
                                Text(transfer.name)
                                    .lineLimit(1)
                                    .foregroundColor(DsTheme.onBackground)
                                Spacer()
                                Button("Cancel") { onCancel(transfer.id) }
                                    .font(.system(size: 14))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                            }
                            ProgressView(value: transfer.percent, total: 100)
                                .tint(DsTheme.blue)
                            HStack {
                                Text("\(String(format: "%.0f", transfer.percent))% of \(dsFormatSize(transfer.size))")
                                Spacer()
                                Text(transfer.running
                                     ? "\(dsFormatSize(transfer.speed))/s \(transfer.eta)".trimmingCharacters(in: .whitespaces)
                                     : transfer.status)
                            }
                            .font(.system(size: 12))
                            .foregroundColor(DsTheme.onSurfaceVariant)
                        }
                        .padding(12)
                        .background(DsTheme.surface)
                        .cornerRadius(10)
                    }
                }
                .padding(12)
            }
        }
    }

    @ViewBuilder
    private var finishedList: some View {
        if snapshot.finished.isEmpty {
            Text("Nothing finished yet.")
                .foregroundColor(DsTheme.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 6) {
                    ForEach(snapshot.finished) { item in
                        HStack(spacing: 8) {
                            Image(systemName: item.done ? "doc" : "xmark.circle")
                                .font(.system(size: 16))
                                .foregroundColor(item.done ? DsTheme.green : DsTheme.error)
                            Text(item.name)
                                .lineLimit(1)
                                .foregroundColor(DsTheme.onBackground)
                            Spacer()
                            Text(dsFormatSize(item.size))
                                .font(.system(size: 12))
                                .foregroundColor(DsTheme.onSurfaceVariant)
                        }
                        .padding(.vertical, 2)
                    }
                }
                .padding(12)
            }
        }
    }

    @ViewBuilder
    private var logList: some View {
        if snapshot.errors.isEmpty {
            Text("No messages.")
                .foregroundColor(DsTheme.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(snapshot.errors, id: \.self) { error in
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(DsTheme.error)
                    }
                }
                .padding(12)
            }
        }
    }
}
