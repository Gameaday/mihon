package ephyra.telemetry

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

internal fun PackageInfo.getCertificateFingerprints(): List<String> {
    val signingInfo = signingInfo!!
    return if (signingInfo.hasMultipleSigners()) {
        signingInfo.apkContentsSigners
    } else {
        signingInfo.signingCertificateHistory
    }
        ?.map(Signature::getCertificateFingerprint)
        ?.toList()
        ?: emptyList()
}

internal val SignatureFlags = PackageManager.GET_SIGNING_CERTIFICATES

@OptIn(ExperimentalStdlibApi::class)
private val CertificateFingerprintHexFormat = HexFormat {
    upperCase = true
    bytes {
        byteSeparator = ":"
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun Signature.getCertificateFingerprint(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .toHexString(CertificateFingerprintHexFormat)
}
