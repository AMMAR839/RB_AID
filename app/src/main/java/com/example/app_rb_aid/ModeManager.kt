package com.example.app_rb_aid

object ModeManager {
    enum class Mode { OFFLINE, ONLINE }
    @Volatile var mode: Mode = Mode.ONLINE
}
