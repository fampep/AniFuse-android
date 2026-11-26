// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A playlist or segment that the CDN has already refused should not sit
 * through Media3's default retries before the next mirror starts.
 */
@UnstableApi
internal class FastFailLoadPolicy : DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 2) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        val code = (exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            ?: httpStatusOf(exception)
        if (code != null && (
                code == 401 || code == 403 || code == 404 || code == 410 ||
                    code == 416 || code == 429 || code in 500..599
                )
        ) {
            return C.TIME_UNSET
        }
        if (exception is UnknownHostException ||
            exception is SocketTimeoutException ||
            exception.cause is UnknownHostException ||
            exception.cause is SocketTimeoutException
        ) {
            // One short breath, then the next mirror — not Media3's long backoff.
            return if (loadErrorInfo.errorCount <= 1) 180L else C.TIME_UNSET
        }
        if (loadErrorInfo.errorCount >= 2) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 2
}
