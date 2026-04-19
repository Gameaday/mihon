package ephyra.telemetry

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

internal fun PackageInfo.getCertificateFingerprints(): List<String> {
    // signingInfo may be null on API < 28 or when signing certificates were not requested
    // or are otherwise unavailable.  Return an empty list in that case so the
    // production-app check conservatively returns false.
    val signingInfo = signingInfo ?: return emptyList()
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
