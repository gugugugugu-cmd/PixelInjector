package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.content.pm.PackageManager
import java.io.IOException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val OMNI_JA = "assets/omni.ja"
private const val APP_CONSTANTS = "modules/AppConstants.sys.mjs"
private const val APP_CONSTANTS_MAX_BYTES = 256 * 1024
private val APP_VERSION = Regex("MOZ_APP_VERSION:\\s*\"([^\"]+)\"")

class Browser(
    val packageName: String,
    val label: String,
    val versionName: String,
    val gecko: String?,
)

fun readGecko(apkPath: String): String =
    ZipFile(apkPath).use { apk ->
        val omni = apk.getEntry(OMNI_JA) ?: throw IOException("no $OMNI_JA")
        ZipInputStream(apk.getInputStream(omni).buffered()).use { stream ->
            generateSequence { stream.nextEntry }
                .firstOrNull { it.name == APP_CONSTANTS }
                ?: throw IOException("no $APP_CONSTANTS")
            val text = stream.readNBytes(APP_CONSTANTS_MAX_BYTES).decodeToString()
            APP_VERSION.find(text)?.groupValues?.get(1) ?: throw IOException("no MOZ_APP_VERSION")
        }
    }

fun installedBrowsers(
    context: Context,
    packages: Collection<String>,
): List<Browser> {
    val pm = context.packageManager
    return packages.mapNotNull { pkg ->
        val info =
            try {
                pm.getPackageInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
        val application = info.applicationInfo ?: return@mapNotNull null
        val gecko =
            try {
                readGecko(application.sourceDir)
            } catch (e: IOException) {
                null
            }
        Browser(pkg, application.loadLabel(pm).toString(), info.versionName.orEmpty(), gecko)
    }
}
