package org.demonsaw.app

/**
 * The JNI surface. Every method must be called from the same thread; the
 * ViewModel funnels all calls through a single-threaded dispatcher.
 */
object Native {
    init {
        System.loadLibrary("demonsaw")
    }

    external fun start(configPath: String): Boolean
    external fun stop()
    external fun snapshot(): String
    external fun chat(text: String): Boolean
    external fun join(): Boolean
    external fun browse(): Boolean
    external fun isRunning(): Boolean
}
