import SwiftUI

// Port of BrowseScreen()
struct BrowseView: View {
    let key: String
    let title: String
    let snapshot: Snapshot
    let onOpen: (String, String) -> Void
    let onDownload: (String) -> Void
    let onDownloadAll: (String) -> Void
    let onBrowse: (String) -> Void

    private var entries: [NodeEntry] { snapshot.nodes[key] ?? [] }

    var body: some View {
        Group {
            if entries.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "folder")
                        .font(.system(size: 44))
                        .foregroundColor(DsTheme.onSurfaceVariant)
                    Text("Empty share.\nTap refresh to load the contents.")
                        .multilineTextAlignment(.center)
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(entries) { entry in
                        HStack(spacing: 10) {
                            Image(systemName: entry.type == "folder" ? "folder" : "doc")
                                .foregroundColor(entry.type == "folder" ? DsTheme.warn : DsTheme.blue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.name)
                                    .lineLimit(1)
                                    .foregroundColor(DsTheme.onBackground)
                                let detail = "\(dsFormatSize(entry.size)) · \(dsFormatTime(entry.time))"
                                Text(detail)
                                    .font(.system(size: 12))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                            }
                            Spacer()
                            if entry.type == "file" {
                                Button {
                                    onDownload(entry.key)
                                } label: {
                                    Image(systemName: "icloud.and.arrow.down")
                                        .foregroundColor(DsTheme.blue)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if entry.type == "folder" {
                                onBrowse(entry.key)
                                onOpen(entry.key, entry.name)
                            }
                        }
                        .padding(.vertical, 4)
                        .listRowBackground(DsTheme.surface)
                        .listRowSeparatorTint(DsTheme.surfaceHigh)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(DsTheme.background)
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(DsTheme.surface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onDownloadAll(key)
                } label: {
                    Image(systemName: "square.and.arrow.down")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onBrowse(key)
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .background(DsTheme.background)
        .onAppear { onBrowse(key) }
    }
}
