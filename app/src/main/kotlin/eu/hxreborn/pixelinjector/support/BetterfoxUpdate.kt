package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.betterfox.BETTERFOX_MAX_BYTES
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 10_000

sealed interface BetterfoxUpdateState {
    data object Idle : BetterfoxUpdateState

    data object Checking : BetterfoxUpdateState

    data object Current : BetterfoxUpdateState

    data class Updated(
        val betterfox: String,
        val revision: Int,
        val changed: List<String>,
    ) : BetterfoxUpdateState

    data object Unreachable : BetterfoxUpdateState

    data class Missing(
        val status: Int,
    ) : BetterfoxUpdateState

    data object Rejected : BetterfoxUpdateState
}

class BetterfoxStatusException(
    val status: Int,
) : IOException("status $status")

class BetterfoxFetch(
    val json: String,
    val list: BetterfoxList,
)

fun fetchBetterfox(url: String = BuildConfig.BETTERFOX_URL): BetterfoxFetch {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) throw BetterfoxStatusException(status)
        val bytes = connection.inputStream.use { it.readNBytes(BETTERFOX_MAX_BYTES + 1) }
        if (bytes.size > BETTERFOX_MAX_BYTES) throw IOException("list too large")
        val json = bytes.decodeToString()
        return BetterfoxFetch(json, BetterfoxList.parse(json))
    } finally {
        connection.disconnect()
    }
}
