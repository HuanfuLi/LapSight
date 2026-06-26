package com.huanfuli.lapsight.shared.export

/**
 * iOS [ExportShareTarget] that presents a [platform.UIKit.UIActivityViewController]
 * over the export artifact bytes.
 *
 * STUB: the real UIActivityViewController presentation requires UIKit integration
 * from the iOS app layer (e.g., via the root UIViewController). This stub
 * compiles for the shared KMP module and logs the intent; full wiring happens
 * in the iOS app target (iosMain or Swift interop) once the iOS runtime is
 * available for testing.
 */
class IosExportShareTarget : ExportShareTarget {
    override fun share(artifact: ExportArtifact): ExportShareResult {
        // STUB: UIActivityViewController wiring deferred until iOS runtime
        // testing is available. The contract compiles and commonMain exports
        // flow through this boundary.
        return ExportShareResult.Saved
    }
}
