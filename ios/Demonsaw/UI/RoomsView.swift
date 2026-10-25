import SwiftUI

// Port of RoomsPane() + RoomScreen()
struct RoomsPane: View {
    let snapshot: Snapshot
    let onJoin: (String) -> Void
    let onQuit: (String) -> Void

    var body: some View {
        Group {
            if snapshot.rooms.isEmpty {
                Text("No rooms. Rooms are defined in the router config.")
                    .multilineTextAlignment(.center)
                    .foregroundColor(DsTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
            } else {
                List {
                    ForEach(snapshot.rooms) { room in
                        HStack(spacing: 10) {
                            Image(systemName: "door.left.hand.open")
                                .foregroundColor(dsParseHex(room.color))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(room.name)
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(DsTheme.onBackground)
                                Text("\(room.size) member(s)")
                                    .font(.system(size: 12))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                            }
                            Spacer()
                            if room.joined {
                                Button("Leave") { onQuit(room.id) }
                                    .font(.system(size: 14))
                                    .foregroundColor(DsTheme.onSurfaceVariant)
                                NavigationLink(value: RoomRoute(id: room.id)) {
                                    Text("Open")
                                        .font(.system(size: 14))
                                        .foregroundColor(DsTheme.blue)
                                }
                                .buttonStyle(.plain)
                            } else {
                                Button("Join") { onJoin(room.id) }
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(DsTheme.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(8)
                                    .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 6)
                        .listRowBackground(DsTheme.surface)
                        .listRowSeparatorTint(DsTheme.surfaceHigh)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(DsTheme.background)
                .padding(.top, 8)
            }
        }
    }
}

// Port of RoomScreen()
struct RoomView: View {
    let room: Room?
    let snapshot: Snapshot
    let onSend: (String) -> Void

    @State private var draft = ""
    @Environment(\.dismiss) private var dismiss

    private var chat: [Message] { room?.chat ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    if chat.isEmpty {
                        Text("No messages in this room yet.")
                            .foregroundColor(DsTheme.onSurfaceVariant)
                            .padding(.top, 120)
                    } else {
                        LazyVStack(spacing: 8) {
                            ForEach(chat) { message in
                                MessageBubble(message: message, mine: message.name == snapshot.name)
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
        .navigationTitle(room?.name ?? "Room")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(DsTheme.surface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .background(DsTheme.background)
    }
}
