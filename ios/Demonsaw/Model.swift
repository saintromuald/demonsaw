import Foundation

// Port of org.demonsaw.app.Model.kt - JSON snapshot decoding.

struct Message: Identifiable {
    let id = UUID()
    let name: String
    let color: String
    let text: String
    let time: Int64
}

struct Peer: Identifiable {
    let id: String
    let name: String
    let color: String
    let mute: Bool
    let pm: [Message]
}

struct Room: Identifiable {
    let id: String
    let name: String
    let color: String
    let size: Int64
    let joined: Bool
    let chat: [Message]
}

struct Share: Identifiable {
    let id = UUID()
    let type: String
    let path: String
    let name: String
    let size: Int64
}

struct Transfer: Identifiable {
    let id: String
    let name: String
    let size: Int64
    let percent: Double
    let speed: Int64
    let eta: String
    let status: String
    let done: Bool
    let errored: Bool
    let paused: Bool
    let running: Bool
}

struct Finished: Identifiable {
    let id = UUID()
    let name: String
    let size: Int64
    let status: String
    let done: Bool
    let errored: Bool
}

struct NodeEntry: Identifiable {
    let id = UUID()
    let type: String // "file" | "folder"
    let key: String
    let nodeId: String
    let name: String
    let size: Int64
    let time: Int64
    let ext: String
    let drive: Bool
}

struct Snapshot {
    var running = false
    var name = ""
    var color = ""
    var selfId = ""
    var group = ""
    var status = "none"
    var clients: [Peer] = []
    var chat: [Message] = []
    var rooms: [Room] = []
    var browse: [NodeEntry] = []
    var nodes: [String: [NodeEntry]] = [:]
    var shares: [Share] = []
    var downloads: [Transfer] = []
    var uploads: [Transfer] = []
    var finished: [Finished] = []
    var searchText = ""
    var searchResults: [NodeEntry] = []
    var errors: [String] = []
    var downloadPath = ""

    var groupLabel: String {
        if group.count >= 16 {
            let upper = group.uppercased()
            return "\(upper.prefix(8)) \(upper.suffix(8))"
        }
        return "No group"
    }
}

private func jsonString(_ dict: [String: Any], _ key: String) -> String {
    (dict[key] as? String) ?? ""
}

private func jsonInt(_ dict: [String: Any], _ key: String) -> Int64 {
    (dict[key] as? NSNumber)?.int64Value ?? 0
}

private func jsonDouble(_ dict: [String: Any], _ key: String) -> Double {
    (dict[key] as? NSNumber)?.doubleValue ?? 0
}

private func jsonBool(_ dict: [String: Any], _ key: String) -> Bool {
    (dict[key] as? NSNumber)?.boolValue ?? false
}

private func parseMessage(_ dict: [String: Any]) -> Message {
    Message(
        name: jsonString(dict, "name"),
        color: jsonString(dict, "color"),
        text: jsonString(dict, "text"),
        time: jsonInt(dict, "time")
    )
}

private func parseMessages(_ array: [[String: Any]]?) -> [Message] {
    array?.map(parseMessage) ?? []
}

private func parseNodeEntry(_ dict: [String: Any]) -> NodeEntry {
    NodeEntry(
        type: jsonString(dict, "type"),
        key: jsonString(dict, "key"),
        nodeId: jsonString(dict, "id"),
        name: jsonString(dict, "name"),
        size: jsonInt(dict, "size"),
        time: jsonInt(dict, "time"),
        ext: jsonString(dict, "ext"),
        drive: jsonBool(dict, "drive")
    )
}

private func parseTransfers(_ array: [[String: Any]]?) -> [Transfer] {
    array?.map { dict in
        Transfer(
            id: jsonString(dict, "id"),
            name: jsonString(dict, "name"),
            size: jsonInt(dict, "size"),
            percent: jsonDouble(dict, "percent"),
            speed: jsonInt(dict, "speed"),
            eta: jsonString(dict, "eta"),
            status: jsonString(dict, "status"),
            done: jsonBool(dict, "done"),
            errored: jsonBool(dict, "errored"),
            paused: jsonBool(dict, "paused"),
            running: jsonBool(dict, "running")
        )
    } ?? []
}

func parseSnapshot(_ raw: String) -> Snapshot {
    guard !raw.isEmpty,
          let data = raw.data(using: .utf8),
          let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    else {
        return Snapshot()
    }

    var snapshot = Snapshot()
    snapshot.running = jsonBool(json, "running")
    snapshot.name = jsonString(json, "name")
    snapshot.color = jsonString(json, "color")
    snapshot.selfId = jsonString(json, "self")
    snapshot.group = jsonString(json, "group")
    snapshot.status = jsonString(json, "status") == "" ? "none" : jsonString(json, "status")
    snapshot.chat = parseMessages(json["chat"] as? [[String: Any]])
    snapshot.downloadPath = jsonString(json, "download_path")

    if let array = json["clients"] as? [[String: Any]] {
        snapshot.clients = array.map { dict in
            Peer(
                id: jsonString(dict, "id"),
                name: jsonString(dict, "name"),
                color: jsonString(dict, "color"),
                mute: jsonBool(dict, "mute"),
                pm: parseMessages(dict["pm"] as? [[String: Any]])
            )
        }
    }

    if let array = json["rooms"] as? [[String: Any]] {
        snapshot.rooms = array.map { dict in
            Room(
                id: jsonString(dict, "id"),
                name: jsonString(dict, "name"),
                color: jsonString(dict, "color"),
                size: jsonInt(dict, "size"),
                joined: jsonBool(dict, "joined"),
                chat: parseMessages(dict["chat"] as? [[String: Any]])
            )
        }
    }

    if let array = json["browse"] as? [[String: Any]] {
        snapshot.browse = array.map { dict in
            NodeEntry(
                type: "client",
                key: jsonString(dict, "key"),
                nodeId: "",
                name: jsonString(dict, "name"),
                size: jsonInt(dict, "size"),
                time: 0,
                ext: "",
                drive: false
            )
        }
    }

    if let nodes = json["nodes"] as? [String: Any] {
        var parsed: [String: [NodeEntry]] = [:]
        for (key, value) in nodes {
            guard let node = value as? [String: Any],
                  let children = node["children"] as? [[String: Any]] else { continue }
            parsed[key] = children.map(parseNodeEntry)
        }
        snapshot.nodes = parsed
    }

    if let array = json["shares"] as? [[String: Any]] {
        snapshot.shares = array.map { dict in
            Share(
                type: jsonString(dict, "type"),
                path: jsonString(dict, "path"),
                name: jsonString(dict, "name"),
                size: jsonInt(dict, "size")
            )
        }
    }

    snapshot.downloads = parseTransfers(json["downloads"] as? [[String: Any]])
    snapshot.uploads = parseTransfers(json["uploads"] as? [[String: Any]])

    if let array = json["finished"] as? [[String: Any]] {
        snapshot.finished = array.map { dict in
            Finished(
                name: jsonString(dict, "name"),
                size: jsonInt(dict, "size"),
                status: jsonString(dict, "status"),
                done: jsonBool(dict, "done"),
                errored: jsonBool(dict, "errored")
            )
        }
    }

    if let search = json["search"] as? [String: Any] {
        snapshot.searchText = jsonString(search, "text")
        if let results = search["results"] as? [[String: Any]] {
            snapshot.searchResults = results.map(parseNodeEntry)
        }
    }

    if let array = json["errors"] as? [String] {
        snapshot.errors = array
    }

    return snapshot
}
