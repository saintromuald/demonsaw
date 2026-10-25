package org.demonsaw.app

import org.json.JSONObject

data class Message(
    val name: String,
    val color: String,
    val text: String,
    val time: Long,
)

data class Peer(
    val name: String,
    val color: String,
    val status: String,
)

data class Snapshot(
    val running: Boolean = false,
    val name: String = "",
    val color: String = "",
    val group: String = "",
    val status: String = "none",
    val clients: List<Peer> = emptyList(),
    val chat: List<Message> = emptyList(),
) {
    val groupLabel: String
        get() = if (group.length >= 16) {
            "${group.substring(0, 8).uppercase()} ${group.substring(group.length - 8).uppercase()}"
        } else {
            "No group"
        }
}

fun parseSnapshot(raw: String): Snapshot {
    if (raw.isBlank()) return Snapshot()

    val json = JSONObject(raw)

    val clients = buildList {
        val array = json.optJSONArray("clients") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Peer(
                    name = item.optString("name"),
                    color = item.optString("color"),
                    status = item.optString("status", "none"),
                ),
            )
        }
    }

    val chat = buildList {
        val array = json.optJSONArray("chat") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Message(
                    name = item.optString("name"),
                    color = item.optString("color"),
                    text = item.optString("text"),
                    time = item.optLong("time"),
                ),
            )
        }
    }

    return Snapshot(
        running = json.optBoolean("running", false),
        name = json.optString("name"),
        color = json.optString("color"),
        group = json.optString("group"),
        status = json.optString("status", "none"),
        clients = clients,
        chat = chat,
    )
}
