import Foundation

// Command ids - keep in sync with demonsaw::engine::command_type
enum EngineCommand: Int32 {
    case chat = 1
    case pm = 2
    case roomJoin = 3
    case roomQuit = 4
    case roomChat = 5
    case browse = 6
    case mute = 7
    case shareAdd = 8
    case shareRemove = 9
    case download = 10
    case cancelDownload = 11
    case cancelUpload = 12
    case setDownloadPath = 13
    case search = 14
    case downloadAll = 15
    case join = 16
    case roomsRefresh = 17
}

// The C surface. Every method must be called from the same thread; the
// AppViewModel funnels all calls through a single serial queue.
enum Native {
    static func start(configPath: String) -> Bool {
        demonsaw_start(configPath) != 0
    }

    static func stop() {
        demonsaw_stop()
    }

    static func snapshot() -> String {
        guard let ptr = demonsaw_snapshot() else { return "" }
        defer { demonsaw_free(UnsafeMutableRawPointer(mutating: ptr)) }
        return String(cString: ptr)
    }

    @discardableResult
    static func command(_ type: EngineCommand, _ a: String = "", _ b: String = "") -> String {
        let result = a.withCString { pa in
            b.withCString { pb in
                demonsaw_command(type.rawValue, pa, pb)
            }
        }
        guard let ptr = result else { return "" }
        defer { demonsaw_free(UnsafeMutableRawPointer(mutating: ptr)) }
        return String(cString: ptr)
    }

    static func isRunning() -> Bool {
        demonsaw_is_running() != 0
    }
}
