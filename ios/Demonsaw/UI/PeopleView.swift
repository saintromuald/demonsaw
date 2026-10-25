import SwiftUI

// Port of PeoplePane() + PmScreen()
struct PeoplePane: View {
    let snapshot: Snapshot
    let onMute: (String, Bool) -> Void
    let onRefresh: () -> Void
    let onBrowse: (String, String) -> Void

    private var peers: [Peer] { snapshot.clients.filter { $0.id != snapshot.selfId } }
    private var sharers: [NodeEntry] { snapshot.browse }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(peers.count) client(s) online")
                    .font(.system(size: 13))
                    .foregroundColor(DsTheme.onSurfaceVariant)
                Spacer()
                Button("Refresh") { onRefresh() }
                    .font(.system(size: 14))
                    .foregroundColor(DsTheme.blue)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            if peers.isEmpty && sharers.isEmpty {
                Text("No one else is here yet.\nShare the router and passphrase with your friends.")
                    .multilineTextAlignment(.center)
                    .foregroundColor(DsTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
            } else {
                List {
                    ForEach(peers) { peer in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(dsParseHex(peer.color))
                                .frame(width: 10, height: 10)
                            Text(peer.name.isEmpty ? "unknown" : peer.name)
                                .foregroundColor(DsTheme.onBackground)
                            Spacer()
                            if peer.mute {
                                Text("muted")
                                    .font(.system(size: 11))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                            }
                            NavigationLink(value: PmRoute(id: peer.id)) {
                                Text("PM")
                                    .font(.system(size: 14))
                                    .foregroundColor(DsTheme.blue)
                            }
                            .buttonStyle(.plain)
                            Button(peer.mute ? "Unmute" : "Mute") { onMute(peer.id, !peer.mute) }
                                .font(.system(size: 14))
                                .foregroundColor(DsTheme.onSurfaceVariant)
                        }
                        .padding(.vertical, 4)
                        .listRowBackground(DsTheme.surface)
                        .listRowSeparatorTint(DsTheme.surfaceHigh)
                    }

                    if !sharers.isEmpty {
                        Text("Sharing")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(DsTheme.blue)
                            .listRowBackground(DsTheme.background)
                    }

                    ForEach(sharers) { sharer in
                        HStack(spacing: 10) {
                            Image(systemName: "folder")
                                .foregroundColor(dsParseHex("0c9bdc"))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(sharer.name)
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(DsTheme.onBackground)
                                Text(dsFormatSize(sharer.size))
                                    .font(.system(size: 12))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                            }
                            Spacer()
                            Text("Browse")
                                .font(.system(size: 13))
                                .foregroundColor(DsTheme.blue)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { onBrowse(sharer.key, sharer.name) }
                        .listRowBackground(DsTheme.surfaceHigh)
                        .listRowSeparatorTint(DsTheme.surfaceHigh)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(DsTheme.background)
            }
        }
    }
}

// Port of PmScreen()
struct PmView: View {
    let peer: Peer?
    let onSend: (String) -> Void

    @State private var draft = ""

    private var chat: [Message] { peer?.pm ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    if chat.isEmpty {
                        Text("Start a private conversation.")
                            .foregroundColor(DsTheme.onSurfaceVariant)
                            .padding(.top, 120)
                    } else {
                        LazyVStack(spacing: 8) {
                            ForEach(chat) { message in
                                MessageBubble(message: message, mine: message.name != peer?.name)
                                    .id(message.id)
                            }
                        }
                        .padding(12)
                    }
                }
                .onChange(of: chat.count) { _ in
                    if let last = chat.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }
            MessageInput(draft: $draft, onSend: {
                if !$0.trimmingCharacters(in: .whitespaces).isEmpty {
                    onSend($0)
                    draft = ""
                }
            })
        }
        .navigationTitle(peer?.name ?? "Private message")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(DsTheme.surface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .background(DsTheme.background)
    }
}
