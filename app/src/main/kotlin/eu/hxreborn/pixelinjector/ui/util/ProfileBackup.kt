package eu.hxreborn.pixelinjector.ui.util

import com.topjohnwu.superuser.Shell
import eu.hxreborn.pixelinjector.betterfox.BrowserPrefs
import eu.hxreborn.pixelinjector.betterfox.ProfileBackupMeta
import eu.hxreborn.pixelinjector.betterfox.restoreTouchedPrefs
import eu.hxreborn.pixelinjector.betterfox.touchedPrefLines
import eu.hxreborn.pixelinjector.betterfox.userPrefValues
import java.io.File

private const val USER_JS = "user.js"
private const val PREFS_JS = "prefs.js"
private const val META = "meta.json"
private const val SHA256_HEX = 64
private val PROFILE_NAME = Regex("[A-Za-z0-9]+\\.default[A-Za-z0-9._-]*")

object ProfileBackup {
    class Entry(
        val packageName: String,
        val profile: String,
        val taken: Long,
    )

    sealed interface Outcome {
        data object Denied : Outcome

        data class Done(
            val packages: Set<String>,
        ) : Outcome

        data class Failed(
            val message: String,
        ) : Outcome
    }

    fun entries(store: File): List<Entry> =
        store
            .listFiles()
            .orEmpty()
            .flatMap { packageDir ->
                packageDir.listFiles().orEmpty().mapNotNull { profileDir ->
                    readMeta(profileDir)?.let { Entry(packageDir.name, profileDir.name, it.taken) }
                }
            }.sortedWith(compareBy({ it.packageName }, { it.profile }))

    fun backUp(
        store: File,
        scratch: File,
        packages: Set<String>,
        names: Set<String>,
    ): Outcome {
        if (!isRoot()) return Outcome.Denied
        val covered = sortedSetOf<String>()
        for (pkg in packages) {
            for (profile in profiles(pkg)) {
                val error = backUpProfile(File(store, "$pkg/$profile"), scratch, profileDirectory(pkg, profile), names)
                if (error != null) return Outcome.Failed("$pkg/$profile $error")
                covered += pkg
            }
        }
        return Outcome.Done(covered)
    }

    fun restore(
        store: File,
        scratch: File,
        packageName: String,
    ): Outcome {
        if (!isRoot()) return Outcome.Denied
        val restored = sortedSetOf<String>()
        for (entry in entries(store).filter { it.packageName == packageName }) {
            val path = profileDirectory(entry.packageName, entry.profile)
            if (!exists(path, "-d")) continue
            Root.forceStop(setOf(entry.packageName))
            val error = restoreProfile(File(store, "${entry.packageName}/${entry.profile}"), scratch, path)
            if (error != null) return Outcome.Failed("${entry.packageName}/${entry.profile} $error")
            restored += entry.packageName
        }
        return Outcome.Done(restored)
    }

    fun browserPrefs(
        pkg: String,
        scratch: File,
    ): BrowserPrefs? {
        if (!isRoot()) return null
        val profile = profiles(pkg).firstOrNull() ?: return null
        scratch.mkdirs()

        fun read(name: String): Map<String, Any> {
            val path = "${profileDirectory(pkg, profile)}/$name"
            val copy = File(scratch, "read-$name")
            if (!exists(path, "-f") || !Root.copyReadable(path, copy)) return emptyMap()
            return userPrefValues(String(copy.readBytes(), Charsets.ISO_8859_1)).also { copy.delete() }
        }
        val file = read(USER_JS)
        return BrowserPrefs(file, read(PREFS_JS) - file.keys)
    }

    private fun backUpProfile(
        dir: File,
        scratch: File,
        profilePath: String,
        names: Set<String>,
    ): String? {
        val prefsCopy = File(scratch, PREFS_JS)
        val current =
            if (exists("$profilePath/$PREFS_JS", "-f") &&
                Root.copyReadable("$profilePath/$PREFS_JS", prefsCopy)
            ) {
                prefsCopy.readBytes()
            } else {
                ByteArray(0)
            }
        val stored = readMeta(dir)
        if (stored != null) {
            val missing = names - stored.prefs.keys
            if (missing.isEmpty()) return null
            return writeMeta(dir, stored.withPrefs(touchedPrefLines(current, missing)))
        }
        dir.mkdirs()
        val userJs = "$profilePath/$USER_JS"
        val sha256 =
            if (exists(userJs, "-f")) {
                val copy = File(dir, USER_JS)
                val source = sha256(userJs)
                if (!Root.copyReadable(userJs, copy) || source == null || sha256(copy.path) != source) return "user.js copy failed"
                source
            } else {
                null
            }
        return writeMeta(dir, ProfileBackupMeta(System.currentTimeMillis(), sha256, touchedPrefLines(current, names)))
    }

    private fun restoreProfile(
        dir: File,
        scratch: File,
        profilePath: String,
    ): String? {
        val meta = readMeta(dir) ?: return "meta unreadable"
        val userJs = "$profilePath/$USER_JS"
        if (meta.userJsSha256 == null) {
            if (!Shell.cmd("rm -f ${quote(userJs)}").exec().isSuccess || exists(userJs, "-e")) return "user.js remove failed"
        } else {
            if (!push(File(dir, USER_JS), userJs, profilePath) || sha256(userJs) != meta.userJsSha256) return "user.js restore failed"
        }
        val prefsCopy = File(scratch, PREFS_JS)
        val prefsJs = "$profilePath/$PREFS_JS"
        val current = if (exists(prefsJs, "-f") && Root.copyReadable(prefsJs, prefsCopy)) prefsCopy.readBytes() else ByteArray(0)
        val restored = File(scratch, "prefs.restored")
        restored.writeBytes(restoreTouchedPrefs(current, meta.prefs))
        if (!push(restored, prefsJs, profilePath)) return "prefs.js write failed"
        restored.delete()
        if (!Root.copyReadable(prefsJs, prefsCopy)) return "prefs.js readback failed"
        return if (touchedPrefLines(prefsCopy.readBytes(), meta.prefs.keys) == meta.prefs) null else "prefs.js mismatch"
    }

    private fun readMeta(dir: File): ProfileBackupMeta? =
        File(dir, META).takeIf { it.isFile }?.let { ProfileBackupMeta.decode(it.readText()) }

    private fun writeMeta(
        dir: File,
        meta: ProfileBackupMeta,
    ): String? {
        val pending = File(dir, "$META.tmp")
        pending.writeText(meta.encode())
        return if (pending.renameTo(File(dir, META))) null else "meta write failed"
    }

    private fun isRoot(): Boolean {
        if (Shell.getShell().isRoot) return true
        Shell.getCachedShell()?.close()
        return false
    }

    private fun profileDirectory(
        pkg: String,
        profile: String,
    ) = "/data/data/$pkg/files/mozilla/$profile"

    private fun profiles(pkg: String): List<String> =
        Shell
            .cmd("ls -1 /data/data/$pkg/files/mozilla 2>/dev/null")
            .exec()
            .out
            .map { it.trim() }
            .filter { PROFILE_NAME.matches(it) }

    private fun exists(
        path: String,
        test: String,
    ) = Shell.cmd("[ $test ${quote(path)} ]").exec().isSuccess

    private fun sha256(path: String): String? =
        Shell
            .cmd("sha256sum ${quote(path)}")
            .exec()
            .out
            .firstOrNull()
            ?.take(SHA256_HEX)

    private fun push(
        source: File,
        target: String,
        profilePath: String,
    ): Boolean {
        val to = quote(target)
        val reference = quote(profilePath)
        return Shell
            .cmd(
                "cat ${quote(source.path)} > $to && chown \$(stat -c %u:%g $reference) $to && " +
                    "set -- \$(ls -Zd $reference) && chcon \"\$1\" $to && chmod 600 $to",
            ).exec()
            .isSuccess
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
