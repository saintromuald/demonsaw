import SwiftUI
import UniformTypeIdentifiers

// Port of SettingsPane() - community info, search, shares, downloads,
// client status, disconnect. File access uses the iOS document picker
// instead of Android's "all files access".
struct SettingsPane: View {
    let snapshot: Snapshot
    let vm: AppViewModel

    @State private var sharePath = ""
    @State private var downloadPath = ""
    @State private var searchText = ""
    @State private var pickerPresented = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                sectionTitle("Community")
                Text(snapshot.groupLabel)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(DsTheme.onBackground)
                Text("Share this group with your friends. They join by using the same router and passphrase.")
                    .font(.system(size: 13))
                    .foregroundColor(DsTheme.onSurfaceVariant)

                divider()

                sectionTitle("Search shared files")
                HStack(spacing: 8) {
                    TextField("Keywords, e.g. ubuntu iso", text: $searchText)
                        .padding(12)
                        .background(DsTheme.surface)
                        .cornerRadius(8)
                        .foregroundColor(DsTheme.onBackground)
                    Button {
                        vm.search(searchText)
                    } label: {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(Circle().fill(DsTheme.blue))
                    }
                }

                ForEach(snapshot.searchResults.prefix(20)) { result in
                    HStack(spacing: 8) {
                        Image(systemName: result.type == "folder" ? "folder" : "doc")
                            .font(.system(size: 14))
                            .foregroundColor(result.type == "folder" ? DsTheme.warn : DsTheme.blue)
                        Text(result.name)
                            .lineLimit(1)
                            .foregroundColor(DsTheme.onBackground)
                        Spacer()
                        Text(dsFormatSize(result.size))
                            .font(.system(size: 12))
                            .foregroundColor(DsTheme.onSurfaceVariant)
                        if result.type == "file" {
                            Button("Get") { vm.download(result.key) }
                                .font(.system(size: 14))
                                .foregroundColor(DsTheme.blue)
                        }
                    }
                    .padding(.vertical, 4)
                }

                divider()

                sectionTitle("My shares")
                Text("iOS sandboxes each app's files. Pick a folder from your Files app to share; peers can browse and download it.")
                    .font(.system(size: 13))
                    .foregroundColor(DsTheme.onSurfaceVariant)

                HStack(spacing: 8) {
                    Text(sharePath.isEmpty ? "No folder selected" : sharePath)
                        .font(.system(size: 13))
                        .lineLimit(1)
                        .foregroundColor(sharePath.isEmpty ? DsTheme.onSurfaceVariant : DsTheme.onBackground)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(DsTheme.surface)
                        .cornerRadius(8)
                    Button {
                        pickerPresented = true
                    } label: {
                        Image(systemName: "folder.badge.plus")
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(Circle().fill(DsTheme.blue))
                    }
                }

                Button {
                    let path = sharePath.trimmingCharacters(in: .whitespaces)
                    if !path.isEmpty {
                        vm.shareAdd(path)
                        sharePath = ""
                    }
                } label: {
                    Text("Share")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(DsTheme.blue.opacity(sharePath.isEmpty ? 0.4 : 1))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                .disabled(sharePath.isEmpty)

                ForEach(snapshot.shares) { share in
                    HStack(spacing: 8) {
                        Image(systemName: share.type == "folder" ? "folder" : "doc")
                            .font(.system(size: 14))
                            .foregroundColor(share.type == "folder" ? DsTheme.warn : DsTheme.blue)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(share.name)
                                .lineLimit(1)
                                .foregroundColor(DsTheme.onBackground)
                            Text("\(dsFormatSize(share.size)) · \(share.path)")
                                .font(.system(size: 11))
                                .lineLimit(1)
                                .foregroundColor(DsTheme.onSurfaceVariant)
                        }
                        Spacer()
                        Button {
                            vm.shareRemove(share.path)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(DsTheme.error)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, 6)
                }
                if snapshot.shares.isEmpty {
                    Text("You are not sharing anything.")
                        .font(.system(size: 13))
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }

                divider()

                sectionTitle("Downloads")
                HStack(spacing: 8) {
                    TextField("Save to", text: $downloadPath)
                        .padding(12)
                        .background(DsTheme.surface)
                        .cornerRadius(8)
                        .foregroundColor(DsTheme.onBackground)
                    Button("Apply") {
                        let path = downloadPath.trimmingCharacters(in: .whitespaces)
                        if !path.isEmpty { vm.setDownloadPath(path) }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .padding(.horizontal, 14)
                    .frame(height: 42)
                    .background(DsTheme.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                Text("Downloaded files are visible in the Files app under On My iPhone → Demonsaw.")
                    .font(.system(size: 12))
                    .foregroundColor(DsTheme.onSurfaceVariant)
                    .padding(.top, 6)

                divider()

                sectionTitle("Client")
                Text(snapshot.name.isEmpty ? "—" : snapshot.name)
                    .foregroundColor(DsTheme.onBackground)
                Text("Status: \(snapshot.status)")
                    .font(.system(size: 13))
                    .foregroundColor(DsTheme.onSurfaceVariant)

                Spacer().frame(height: 24)

                Button {
                    vm.join()
                } label: {
                    Text("Join group")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(DsTheme.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }

                Button {
                    vm.disconnect()
                } label: {
                    Label("Disconnect", systemImage: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 15))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }
                .padding(.top, 8)
            }
            .padding(20)
        }
        .background(DsTheme.background)
        .fileImporter(
            isPresented: $pickerPresented,
            allowedContentTypes: [.folder, .item],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                sharePath = url.path
            }
        }
        .onAppear { downloadPath = snapshot.downloadPath }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(DsTheme.blue)
            .padding(.top, 16)
            .padding(.bottom, 8)
    }

    private func divider() -> some View {
        Rectangle()
            .fill(DsTheme.surfaceHigh)
            .frame(height: 1)
            .padding(.vertical, 16)
    }
}
