package com.huanfuli.lapsight.shared.glasses

/**
 * Platform-free command seam from shared UI to the Android-only Meta DAT bridge.
 * Shared Compose code may call these methods but must never import DAT types.
 */
interface GlassesActions {
    fun register()
    fun pickDevice(id: String)
    fun startCasting()
    fun stopCasting()
    fun setPage(page: HudPage)
    fun openFirmwareUpdate()
    fun openDatAppUpdate()
}

object NoOpGlassesActions : GlassesActions {
    override fun register() = Unit
    override fun pickDevice(id: String) = Unit
    override fun startCasting() = Unit
    override fun stopCasting() = Unit
    override fun setPage(page: HudPage) = Unit
    override fun openFirmwareUpdate() = Unit
    override fun openDatAppUpdate() = Unit
}
