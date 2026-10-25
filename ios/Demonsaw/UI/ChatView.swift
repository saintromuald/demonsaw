import SwiftUI

// Port of MainScreen() - tab scaffold plus navigation to room/PM/browse.
struct MainView: View {
    @ObservedObject var vm: AppViewModel

    @State private var tab = 0
    @State private var browsePath = NavigationPath()

    var body: some View {
        NavigationStack(path: $browsePath) {
            VStack(spacing: 0) {
                topBar
                TabView(selection: $tab) {
                    ChatPane(snapshot: vm.snapshot) { vm.send($0) }
                        .tag(0)
                        .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }
                    RoomsPane(snapshot: vm.snapshot) { vm.joinRoom($0) } onQuit: { vm.quitRoom($0) }
                        .tag(1)
                        .tabItem { Label("Rooms", systemImage: "door.left.hand.open") }
                    PeoplePane(
                        snapshot: vm.snapshot,
                        onMute: { vm.mute($0, value: $1) },
                        onRefresh: { vm.refreshRooms() },
                        onBrowse: { key, name in browsePath.append(BrowseRoute(key: key, title: name)) }
                    )
                    .tag(2)
                    .tabItem { Label("People", systemImage: "person.2") }
                    TransfersPane(snapshot: vm.snapshot, vm: vm)
                        .tag(3)
                        .tabItem { Label("Transfers", systemImage: "arrow.up.arrow.down") }
                    SettingsPane(snapshot: vm.snapshot, vm: vm)
                        .tag(4)
                        .tabItem { Label("Settings", systemImage: "gearshape") }
                }
                .tint(DsTheme.blue)
            }
            .background(DsTheme.background)
            .navigationDestination(for: BrowseRoute.self) { route in
                BrowseView(
                    key: route.key,
                    title: route.title,
                    snapshot: vm.snapshot,
                    onOpen: { key, name in browsePath.append(BrowseRoute(key: key, title: name)) },
                    onDownload: { vm.download($0) },
                    onDownloadAll: { vm.downloadAll($0) },
                    onBrowse: { vm.browse($0) }
                )
            }
            .navigationDestination(for: RoomRoute.self) { route in
                RoomView(
                    room: vm.snapshot.rooms.first { $0.id == route.id },
                    snapshot: vm.snapshot,
                    onSend: { vm.sendRoom(route.id, $0) }
                )
            }
            .navigationDestination(for: PmRoute.self) { route in
                PmView(
                    peer: vm.snapshot.clients.first { $0.id == route.id },
                    onSend: { vm.sendPm(route.id, $0) }
                )
            }
        }
        .tint(DsTheme.blue)
    }

    private var topBar: some View {
        HStack(spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                Text(vm.snapshot.name.isEmpty ? "Demonsaw" : vm.snapshot.name)
                    .font(.headline)
                    .foregroundColor(DsTheme.onBackground)
                HStack(spacing: 6) {
                    StatusDot(status: vm.snapshot.status)
                    Text(vm.snapshot.groupLabel)
                        .font(.system(size: 12))
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(DsTheme.surface)
    }
}

struct BrowseRoute: Hashable {
    let key: String
    let title: String
}

struct RoomRoute: Hashable {
    let id: String
}

struct PmRoute: Hashable {
    let id: String
}

// Port of ChatPane()
struct ChatPane: View {
    let snapshot: Snapshot
    let onSend: (String) -> Void

    @State private var draft = ""

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    if snapshot.chat.isEmpty {
                        emptyState("No messages yet.\nSay hello to the group.")
                            .padding(.top, 120)
                    } else {
                        LazyVStack(spacing: 8) {
                            ForEach(snapshot.chat) { message in
                                MessageBubble(message: message, mine: message.name == snapshot.name)
                                    .id(message.id)
                            }
                        }
                        .padding(12)
                    }
                }
                .onChange(of: snapshot.chat.count) { _ in
                    if let last = snapshot.chat.last {
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
    }

    private func emptyState(_ text: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "person.3")
                .font(.system(size: 44))
                .foregroundColor(DsTheme.onSurfaceVariant)
            Text(text)
                .multilineTextAlignment(.center)
                .foregroundColor(DsTheme.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
    }
}

// Port of MessageBubble()
struct MessageBubble: View {
    let message: Message
    let mine: Bool

    var body: some View {
        VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
            if !mine {
                Text(message.name.isEmpty ? "unknown" : message.name)
                    .font(.system(size: 11))
                    .foregroundColor(dsParseHex(message.color))
                    .padding(.leading, 8)
                    .padding(.bottom, 2)
            }
            VStack(alignment: .trailing, spacing: 2) {
                Text(message.text)
                    .foregroundColor(DsTheme.onBackground)
                if message.time > 0 {
                    Text(dsFormatClock(message.time))
                        .font(.system(size: 10))
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(mine ? DsTheme.blue.opacity(0.85) : DsTheme.surfaceHigh)
            .cornerRadius(14)
        }
        .frame(maxWidth: 320, alignment: mine ? .trailing : .leading)
        .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
    }
}

// Port of MessageInput()
struct MessageInput: View {
    @Binding var draft: String
    let onSend: (String) -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Message", text: $draft, axis: .vertical)
                .lineLimit(1...4)
                .padding(12)
                .background(DsTheme.surface)
                .cornerRadius(10)
                .submitLabel(.send)
                .onSubmit { onSend(draft) }

            Button {
                onSend(draft)
            } label: {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(DsTheme.blue))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .background(DsTheme.background)
    }
}
