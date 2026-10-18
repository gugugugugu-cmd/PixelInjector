package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.os.Build
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.support.ReportBuilder.toHex
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val CODE_ENTRY = Regex("""classes\d*\.dex|resources\.arsc|AndroidManifest\.xml""")
private const val SERVICES_JAR = "/system/framework/services.jar"
private const val BUFFER_BYTES = 1 shl 16

class RomCode(
    val kind: String,
    val path: String,
    val sha256: String,
    val size: Long,
) {
    val name: String get() = File(path).name

    fun toJson(): JSONObject =
        JSONObject()
            .put("kind", kind)
            .put("sha256", sha256)
            .put("size", size)
            .put("file", name)

    fun toLedgerJson(): JSONObject = toJson().put("path", path)

    fun zip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(
                name: String,
                input: InputStream,
            ) {
                zip.putNextEntry(ZipEntry(name))
                input.use { it.copyTo(zip, BUFFER_BYTES) }
                zip.closeEntry()
            }
            ZipFile(path).use { source ->
                val code = source.entries().toList().filter { CODE_ENTRY.matches(it.name) }
                code.forEach { add(it.name, source.getInputStream(it)) }
                if (code.none { it.name.endsWith(".dex") }) {
                    val vdex = vdex() ?: error("no dex and no vdex for $path")
                    add(vdex.name, vdex.inputStream())
                }
            }
        }
        return out.toByteArray()
    }

    private fun vdex(): File? {
        val source = File(path)
        val abi = Build.SUPPORTED_ABIS.first()
        val isa =
            when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm"
                else -> abi
            }
        return File(source.parentFile, "oat/$isa/${source.nameWithoutExtension}.vdex").takeIf {
            it.canRead()
        }
    }

    companion object {
        const val SYSTEMUI = "systemui"
        const val SERVICES = "services"

        val wanted: Boolean get() = !Build.MANUFACTURER.equals("Google", ignoreCase = true)

        fun find(
            context: Context,
            kind: String,
        ): RomCode? {
            val path =
                when (kind) {
                    SYSTEMUI -> {
                        context.packageManager
                            .getApplicationInfo(
                                ModuleConstants.SYSTEMUI_PACKAGE,
                                0,
                            ).sourceDir
                    }

                    SERVICES -> {
                        SERVICES_JAR
                    }

                    else -> {
                        return null
                    }
                }
            val file = File(path)
            val digest = MessageDigest.getInstance("SHA-256")
            DigestOutputStream(OutputStream.nullOutputStream(), digest).use { out ->
                file.inputStream().use { it.copyTo(out, BUFFER_BYTES) }
            }
            return RomCode(kind, path, digest.digest().toHex(), file.length())
        }

        fun from(json: JSONObject?): RomCode? =
            json?.let {
                RomCode(
                    kind = it.optString("kind"),
                    path = it.optString("path"),
                    sha256 = it.optString("sha256"),
                    size = it.optLong("size"),
                )
            }
    }
}
