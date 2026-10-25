package org.demonsaw.app

import org.json.JSONObject

data class Message(
    val name: String,
    val color: String,
    val text: String,
    val time: Long,
)

data class Peer(
    val id: String,
    val name: String,
    val color: String,
    val mute: Boolean,
    val pm: List<Message>,
)

data class Room(
    val id: String,
    val name: String,
    val color: String,
    val size: Long,
    val joined: Boolean,
    val chat: List<Message>,
)

data class Share(
    val type: String,
    val path: String,
    val name: String,
    val size: Long,
)

data class Transfer(
    val id: String,
    val name: String,
    val size: Long,
    val percent: Double,
    val speed: Long,
    val eta: String,
    val status: String,
    val done: Boolean,
    val errored: Boolean,
    val paused: Boolean,
    val running: Boolean,
)

data class Finished(
    val name: String,
    val size: Long,
    val status: String,
    val done: Boolean,
    val errored: Boolean,
)

data class NodeEntry(
    val type: String, // "file" | "folder"
    val key: String,
    val id: String,
    val name: String,
    val size: Long,
    val time: Long,
    val ext: String,
    val drive: Boolean,
)

data class Snapshot(
    val running: Boolean = false,
    val name: String = "",
    val color: String = "",
    val self: String = "",
    val group: String = "",
    val status: String = "none",
    val clients: List<Peer> = emptyList(),
    val chat: List<Message> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val browse: List<NodeEntry> = emptyList(),
    val nodes: Map<String, List<NodeEntry>> = emptyMap(),
    val shares: List<Share> = emptyList(),
    val downloads: List<Transfer> = emptyList(),
    val uploads: List<Transfer> = emptyList(),
    val finished: List<Finished> = emptyList(),
    val searchText: String = "",
    val searchResults: List<NodeEntry> = emptyList(),
    val errors: List<String> = emptyList(),
    val downloadPath: String = "",
) {
    val groupLabel: String
        get() = if (group.length >= 16) {
            "${group.substring(0, 8).uppercase()} ${group.substring(group.length - 8).uppercase()}"
        } else {
            "No group"
        }
}

private fun parseMessage(item: JSONObject): Message = Message(
    name = item.optString("name"),
    color = item.optString("color"),
    text = item.optString("text"),
    time = item.optLong("time"),
)

private fun parseMessages(array: org.json.JSONArray?): List<Message> = buildList {
    if (array == null) return@buildList
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        add(parseMessage(item))
    }
}

private fun parseNodeEntry(item: JSONObject): NodeEntry = NodeEntry(
    type = item.optString("type"),
    key = item.optString("key"),
    id = item.optString("id"),
    name = item.optString("name"),
    size = item.optLong("size"),
    time = item.optLong("time"),
    ext = item.optString("ext"),
    drive = item.optBoolean("drive"),
)

fun parseSnapshot(raw: String): Snapshot {
    if (raw.isBlank()) return Snapshot()

    val json = JSONObject(raw)

    val clients = buildList {
        val array = json.optJSONArray("clients") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Peer(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    color = item.optString("color"),
                    mute = item.optBoolean("mute"),
                    pm = parseMessages(item.optJSONArray("pm")),
                ),
            )
        }
    }

    val rooms = buildList {
        val array = json.optJSONArray("rooms") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Room(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    color = item.optString("color"),
                    size = item.optLong("size"),
                    joined = item.optBoolean("joined"),
                    chat = parseMessages(item.optJSONArray("chat")),
                ),
            )
        }
    }

    val browse = buildList {
        val array = json.optJSONArray("browse") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                NodeEntry(
                    type = "client",
                    key = item.optString("key"),
                    id = "",
                    name = item.optString("name"),
                    size = item.optLong("size"),
                    time = 0,
                    ext = "",
                    drive = false,
                ),
            )
        }
    }

    val nodes = buildMap {
        val obj = json.optJSONObject("nodes") ?: return@buildMap
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val node = obj.optJSONObject(key) ?: continue
            val children = buildList {
                val array = node.optJSONArray("children") ?: return@buildList
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(parseNodeEntry(item))
                }
            }
            put(key, children)
        }
    }

    val shares = buildList {
        val array = json.optJSONArray("shares") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Share(
                    type = item.optString("type"),
                    path = item.optString("path"),
                    name = item.optString("name"),
                    size = item.optLong("size"),
                ),
            )
        }
    }

    fun parseTransfers(name: String): List<Transfer> = buildList {
        val array = json.optJSONArray(name) ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Transfer(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    size = item.optLong("size"),
                    percent = item.optDouble("percent"),
                    speed = item.optLong("speed"),
                    eta = item.optString("eta"),
                    status = item.optString("status"),
                    done = item.optBoolean("done"),
                    errored = item.optBoolean("errored"),
                    paused = item.optBoolean("paused"),
                    running = item.optBoolean("running"),
                ),
            )
        }
    }

    val finished = buildList {
        val array = json.optJSONArray("finished") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Finished(
                    name = item.optString("name"),
                    size = item.optLong("size"),
                    status = item.optString("status"),
                    done = item.optBoolean("done"),
                    errored = item.optBoolean("errored"),
                ),
            )
        }
    }

    val search = json.optJSONObject("search")
    val searchResults = buildList {
        val array = search?.optJSONArray("results") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(parseNodeEntry(item))
        }
    }

    return Snapshot(
        running = json.optBoolean("running", false),
        name = json.optString("name"),
        color = json.optString("color"),
        self = json.optString("self"),
        group = json.optString("group"),
        status = json.optString("status", "none"),
        clients = clients,
        chat = parseMessages(json.optJSONArray("chat")),
        rooms = rooms,
        browse = browse,
        nodes = nodes,
        shares = shares,
        downloads = parseTransfers("downloads"),
        uploads = parseTransfers("uploads"),
        finished = finished,
        searchText = search?.optString("text") ?: "",
        searchResults = searchResults,
        errors = buildList {
            val array = json.optJSONArray("errors") ?: return@buildList
            for (i in 0 until array.length()) add(array.optString(i))
        },
        downloadPath = json.optString("download_path"),
    )
}
