import SwiftUI

@main
struct DemonsawApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    @StateObject private var vm = AppViewModel()

    var body: some View {
        ZStack {
            DsTheme.background.ignoresSafeArea()

            if vm.connecting {
                VStack(spacing: 16) {
                    ProgressView()
                        .tint(DsTheme.blue)
                        .scaleEffect(1.4)
                    Text("Connecting…")
                        .foregroundColor(DsTheme.onSurfaceVariant)
                }
            } else if vm.running {
                MainView(vm: vm)
            } else {
                ConnectView(error: vm.error) { host, port, name, passphrase in
                    vm.connect(host: host, port: port, name: name, passphrase: passphrase)
                }
            }
        }
    }
}

// Port of StatusDot()
struct StatusDot: View {
    let status: String

    var color: Color {
        switch status {
        case "success": return DsTheme.green
        case "pending", "info": return DsTheme.warn
        case "warning": return DsTheme.warnOrange
        case "error": return DsTheme.error
        default: return DsTheme.mutedDot
        }
    }

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 8, height: 8)
    }
}
