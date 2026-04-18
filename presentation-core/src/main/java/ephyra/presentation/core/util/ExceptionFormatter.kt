package ephyra.presentation.core.util

import android.content.Context
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.isOnline
import ephyra.domain.source.model.NoResultsException
import ephyra.domain.source.model.SourceNotInstalledException
import ephyra.i18n.MR
import eu.kanade.tachiyomi.network.HttpException
import java.net.UnknownHostException

context(context: Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return context.stringResource(MR.strings.exception_http, code)
            is UnknownHostException -> {
                return if (!context.isOnline()) {
                    context.stringResource(MR.strings.exception_offline)
                } else {
                    context.stringResource(MR.strings.exception_unknown_host, message ?: "")
                }
            }

            is NoResultsException -> return context.stringResource(MR.strings.no_results_found)
            is SourceNotInstalledException -> return context.stringResource(MR.strings.loader_not_implemented_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
