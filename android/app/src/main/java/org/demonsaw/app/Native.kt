package org.demonsaw.app

/**
 * The JNI surface. Every method must be called from the same thread; the
 * ViewModel funnels all calls through a single-threaded dispatcher.
 */
object Native {
    init {
        System.loadLibrary("demonsaw")
    }

    // Commands (keep in sync with demonsaw::engine::command_type)
    const val CMD_CHAT = 1
    const val CMD_PM = 2
    const val CMD_ROOM_JOIN = 3
    const val CMD_ROOM_QUIT = 4
    const val CMD_ROOM_CHAT = 5
    const val CMD_BROWSE = 6
    const val CMD_MUTE = 7
    const val CMD_SHARE_ADD = 8
    const val CMD_SHARE_REMOVE = 9
    const val CMD_DOWNLOAD = 10
    const val CMD_CANCEL_DOWNLOAD = 11
    const val CMD_CANCEL_UPLOAD = 12
    const val CMD_SET_DOWNLOAD_PATH = 13
    const val CMD_SEARCH = 14
    const val CMD_DOWNLOAD_ALL = 15
    const val CMD_JOIN = 16
    const val CMD_ROOMS_REFRESH = 17

    external fun start(configPath: String): Boolean
    external fun stop()
    external fun snapshot(): String
    external fun command(type: Int, a: String, b: String): String
    external fun isRunning(): Boolean
}
