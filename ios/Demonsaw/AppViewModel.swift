import Foundation
import Combine

// Port of org.demonsaw.app.AppViewModel - single-threaded engine driver
// plus snapshot polling.

final class AppViewModel: ObservableObject {
    @Published var connecting = false
    @Published var snapshot = Snapshot()
    @Published var error: String?
    @Published var notice: String?

    var running: Bool { snapshot.running }

    // Every engine call runs on this serial queue (mirrors the Android
    // single-thread executor - the engine is not thread-safe).
    private let queue = DispatchQueue(label: "demonsaw-native")
    private var pollTimer: Timer?

    private var documentsDir: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private var configPath: URL { documentsDir.appendingPathComponent("client.toml") }

    private var defaultDownloadPath: URL {
        let dir = documentsDir.appendingPathComponent("Downloads", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: - Lifecycle

    func connect(host: String, port: Int, name: String, passphrase: String) {
        connecting = true
        error = nil

        queue.async { [weak self] in
            guard let self else { return }
            do {
                let config = buildConfig(host: host, port: port, name: name, passphrase: passphrase)
                try config.write(to: self.configPath, atomically: true, encoding: .utf8)

                let started = Native.start(configPath: self.configPath.path)
                if started {
                    let downloads = self.defaultDownloadPath.path
                    Native.command(.setDownloadPath, downloads, "")
                }

                DispatchQueue.main.async {
                    if started {
                        self.connecting = false
                        self.startPolling()
                    } else {
                        self.connecting = false
                        self.error = "Could not start Demonsaw. Check the router address and passphrase."
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self.connecting = false
                    self.error = "Could not write the configuration file."
                }
            }
        }
    }

    func disconnect() {
        pollTimer?.invalidate()
        pollTimer = nil
        queue.async { Native.stop() }
        DispatchQueue.main.async {
            self.snapshot = Snapshot()
            self.error = nil
            self.notice = nil
            self.connecting = false
        }
    }

    // MARK: - Commands (each result is {"ok":true} on success)

    func send(_ text: String) { command(.chat, text, "") }
    func sendPm(clientId: String, text: String) { command(.pm, clientId, text) }
    func sendRoom(roomId: String, text: String) { command(.roomChat, roomId, text) }
    func joinRoom(_ roomId: String) { command(.roomJoin, roomId, "") }
    func quitRoom(_ roomId: String) { command(.roomQuit, roomId, "") }
    func browse(_ key: String) { command(.browse, key, "") }
    func mute(_ clientId: String, value: Bool) { command(.mute, clientId, value ? "1" : "0") }
    func shareAdd(_ path: String) { command(.shareAdd, path, "") }
    func shareRemove(_ path: String) { command(.shareRemove, path, "") }
    func download(_ key: String) { command(.download, key, "") }
    func downloadAll(_ key: String) { command(.downloadAll, key, "") }
    func cancelDownload(_ id: String) { command(.cancelDownload, id, "") }
    func cancelUpload(_ id: String) { command(.cancelUpload, id, "") }
    func setDownloadPath(_ path: String) { command(.setDownloadPath, path, "") }
    func search(_ text: String) { command(.search, text, "") }
    func join() { command(.join, "", "") }
    func refreshRooms() { command(.roomsRefresh, "", "") }

    private func command(_ type: EngineCommand, _ a: String, _ b: String, onResult: ((Bool) -> Void)? = nil) {
        queue.async {
            let result = Native.command(type, a, b)
            if let onResult {
                DispatchQueue.main.async { onResult(result.contains("\"ok\":true")) }
            }
        }
    }

    // MARK: - Polling (700 ms, same cadence as Android)

    private func startPolling() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.7, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.queue.async {
                let parsed = parseSnapshot(Native.snapshot())
                DispatchQueue.main.async {
                    self.snapshot = parsed
                    self.connecting = false
                }
            }
        }
    }

    // MARK: - Config builder (port of buildConfig())

    private func buildConfig(host: String, port: Int, name: String, passphrase: String) -> String {
        let safeName = escape(name.isEmpty ? "iOS" : name)
        let safeHost = escape(host)
        let safePass = escape(passphrase)

        var lines: [String] = []
        lines.append("[[client]]")
        lines.append("\tenabled = true")
        lines.append("\tname = \"\(safeName)\"")
        lines.append("\tcolor = \"0c9bdc\"")
        lines.append("\t[[client.router]]")
        lines.append("\t\tenabled = true")
        lines.append("\t\tname = \"Router\"")
        lines.append("\t\taddress = \"\(safeHost)\"")
        lines.append("\t\tport = \(port)")
        if !passphrase.isEmpty {
            lines.append("\t[[client.group]]")
            lines.append("\t\tenabled = true")
            lines.append("\t\tentropy = \"\(safePass)\"")
        }
        return lines.joined(separator: "\n") + "\n"
    }

    private func escape(_ value: String) -> String {
        var out = ""
        for ch in value {
            switch ch {
            case "\\": out += "\\\\"
            case "\"": out += "\\\""
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default: out.append(ch)
            }
        }
        return out
    }
}
