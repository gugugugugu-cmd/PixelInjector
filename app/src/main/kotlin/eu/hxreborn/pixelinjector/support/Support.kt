package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ui.util.Root
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

sealed interface ReleaseCheck {
    val known: Release?

    data object Idle : ReleaseCheck {
        override val known: Release? = null
    }

    data class Checking(
        override val known: Release?,
    ) : ReleaseCheck

    data class Done(
        val release: Release?,
        val checkedAt: Long,
    ) : ReleaseCheck {
        override val known: Release? get() = release
    }

    data class Failed(
        val lastCheckedAt: Long?,
        override val known: Release?,
    ) : ReleaseCheck
}

sealed interface SendResult {
    data class Sent(
        val report: SentReport,
    ) : SendResult

    data class Waiting(
        val report: SentReport,
    ) : SendResult

    data class Rejected(
        val reason: String,
    ) : SendResult
}

class Support(
    private val context: Context,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler {
                    _,
                    e,
                    ->
                    Log.e(ModuleConstants.LOG_TAG, "support task failed reason=${e.message}", e)
                },
        )
    private val ledgerWriter = Dispatchers.IO.limitedParallelism(1)
    private val prefs = context.getSharedPreferences("support", Context.MODE_PRIVATE)
    private val ledger = Ledger(context.filesDir)
    private val sendLock = Mutex()
    private val romCodeLock = Mutex()
    val updater = Updater(context)

    private val _reports = MutableStateFlow(ledger.read())
    val reports: StateFlow<List<SentReport>> = _reports.asStateFlow()

    private val _release = MutableStateFlow<ReleaseCheck>(cachedRelease())
    val release: StateFlow<ReleaseCheck> = _release.asStateFlow()

    private val _install = MutableStateFlow<InstallStep>(InstallStep.Idle)
    val install: StateFlow<InstallStep> = _install.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(prefs.getBoolean(AUTO_CHECK_UPDATES, true))
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _postponedVersion = MutableStateFlow(prefs.getInt(POSTPONED_VERSION, 0))
    val postponedVersion: StateFlow<Int> = _postponedVersion.asStateFlow()

    val updatedFrom: Int? =
        prefs.getInt(LAST_RUN_VERSION, 0).takeIf { it in 1 until BuildConfig.VERSION_CODE }.also {
            prefs.edit { putInt(LAST_RUN_VERSION, BuildConfig.VERSION_CODE) }
        }

    private fun cachedRelease(): ReleaseCheck {
        val body = prefs.getString(RELEASE_BODY, null) ?: return ReleaseCheck.Idle
        val release = runCatching { Release.parse(body) }.getOrNull()
        return ReleaseCheck.Done(release, prefs.getLong(RELEASE_CHECKED_AT, 0))
    }

    private fun mutate(change: (List<SentReport>) -> List<SentReport>) {
        _reports.update(change)
        scope.launch(ledgerWriter) { ledger.write(_reports.value) }
    }

    private fun replace(report: SentReport) =
        mutate { list ->
            list.map { if (it.id == report.id) report else it }
        }

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit { putBoolean(AUTO_CHECK_UPDATES, enabled) }
        _autoCheckUpdates.value = enabled
    }

    fun postponeUpdate(versionCode: Int) {
        prefs.edit { putInt(POSTPONED_VERSION, versionCode) }
        _postponedVersion.value = versionCode
    }

    fun noteKeyFor(replyTo: String?): Hpke.NoteKey =
        replyTo?.let { root -> _reports.value.firstOrNull { it.id == root }?.noteKey }
            ?: Hpke.newNoteKey()

    fun markNotesSeen() = mutate { list -> list.map { it.copy(noteSeen = true) } }

    suspend fun send(
        encrypted: EncryptedReport,
        message: String,
        tweak: String?,
        replyTo: String?,
    ): SendResult {
        val report =
            SentReport(
                id = encrypted.id,
                short = null,
                tweak = tweak,
                sentAt = System.currentTimeMillis(),
                status = ReportStatus.WAITING,
                fixedIn = null,
                note = null,
                readToken = encrypted.readToken,
                deleteToken = encrypted.deleteToken,
                replyTo = replyTo,
                build = Build.ID,
                message = message.ifBlank { null },
                noteKeyPrivate =
                    Base64.getEncoder().encodeToString(
                        KeyWrap.wrap(encrypted.noteKey.privatePkcs8),
                    ),
                noteKeyPublic = Base64.getEncoder().encodeToString(encrypted.noteKey.publicRaw),
                romCode = encrypted.romCode,
            )
        ledger.envelope(report.id).writeBytes(encrypted.envelope)
        ledger.meta(report.id).writeBytes(encrypted.meta)
        mutate { listOf(report) + it }
        return upload(report)
    }

    private suspend fun upload(report: SentReport): SendResult =
        sendLock.withLock {
            val current =
                _reports.value.firstOrNull { it.id == report.id }
                    ?: return@withLock SendResult.Rejected("report deleted")
            if (current.status != ReportStatus.WAITING) {
                return@withLock if (current.status == ReportStatus.REJECTED) {
                    SendResult.Rejected(current.error ?: "rejected")
                } else {
                    SendResult.Sent(current)
                }
            }
            val envelope = ledger.envelope(report.id).takeIf { it.exists() }?.readBytes()
            val meta = ledger.meta(report.id).takeIf { it.exists() }?.readBytes()
            if (envelope == null || meta == null) return SendResult.Rejected("missing payload")
            when (
                val reply =
                    ReportClient.putReport(
                        report.id,
                        meta,
                        envelope,
                        ReportBuilder.tokenHash(report.readToken),
                        ReportBuilder.tokenHash(report.deleteToken),
                        report.replyTo,
                    )
            ) {
                is ApiResponse.Ok -> {
                    val json = runCatching { JSONObject(reply.body) }.getOrNull()
                    if (json == null) {
                        Log.w(
                            ModuleConstants.LOG_TAG,
                            "report queued id=${report.id} reason=reply is not JSON code=${reply.code}",
                        )
                        replace(report.copy(error = "reply is not JSON"))
                        return@withLock SendResult.Waiting(report)
                    }
                    Log.i(ModuleConstants.LOG_TAG, "report sent id=${report.id} code=${reply.code}")
                    val sent =
                        report.copy(
                            short = json.optStringOrNull("short"),
                            status = ReportStatus.RECEIVED,
                            error = null,
                        )
                    ledger.deletePayload(report.id)
                    replace(sent)
                    if (sent.romCode != null) scope.launch { uploadRomCode(sent.id) }
                    SendResult.Sent(sent)
                }

                is ApiResponse.Retryable -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report queued id=${report.id} reason=${reply.reason}",
                    )
                    replace(report.copy(error = reply.reason))
                    SendResult.Waiting(report)
                }

                is ApiResponse.Refused -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report refused id=${report.id} code=${reply.code} reason=${reply.reason}",
                    )
                    ledger.deletePayload(report.id)
                    replace(report.copy(status = ReportStatus.REJECTED, error = reply.reason))
                    SendResult.Rejected(reply.reason)
                }
            }
        }

    suspend fun resend(report: SentReport): SendResult = upload(report)

    private fun updateReport(
        id: String,
        change: (SentReport) -> SentReport,
    ) = mutate { list -> list.map { if (it.id == id) change(it) else it } }

    private suspend fun uploadRomCode(id: String) =
        romCodeLock.withLock {
            val report = _reports.value.firstOrNull { it.id == id } ?: return@withLock
            val code = report.romCode?.takeUnless { report.romCodeUploaded } ?: return@withLock
            if (ReportClient.hasAttachment(code.sha256)) {
                Log.i(ModuleConstants.LOG_TAG, "rom code present id=$id kind=${code.kind}")
                return@withLock updateReport(id) { it.copy(romCodeUploaded = true) }
            }
            val reply =
                runCatching {
                    ReportClient.putAttachment(
                        code.sha256,
                        id,
                        report.deleteToken,
                        ReportBuilder.sealRomCode(code),
                    )
                }.getOrElse { ApiResponse.Refused(0, it.message ?: it.javaClass.simpleName) }
            when (reply) {
                is ApiResponse.Ok -> {
                    Log.i(
                        ModuleConstants.LOG_TAG,
                        "rom code sent id=$id kind=${code.kind} code=${reply.code}",
                    )
                    updateReport(id) { it.copy(romCodeUploaded = true) }
                }

                is ApiResponse.Retryable -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code queued id=$id kind=${code.kind} reason=${reply.reason}",
                    )
                }

                is ApiResponse.Refused -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code refused id=$id kind=${code.kind} code=${reply.code} reason=${reply.reason}",
                    )
                    updateReport(id) { it.copy(romCode = null) }
                }
            }
        }

    fun retryWaiting() {
        scope.launch {
            _reports.value.filter { it.status == ReportStatus.WAITING }.forEach { upload(it) }
            _reports.value
                .filter {
                    it.romCode != null && !it.romCodeUploaded &&
                        it.status != ReportStatus.WAITING
                }.forEach { uploadRomCode(it.id) }
        }
    }

    fun refreshStatuses() {
        scope.launch {
            val tracked =
                _reports.value.filter {
                    it.status != ReportStatus.WAITING &&
                        it.status != ReportStatus.REJECTED
                }
            if (tracked.isEmpty()) return@launch
            val reply =
                ReportClient.statuses(tracked.map { it.id to it.readToken }) as? ApiResponse.Ok
                    ?: return@launch
            val byId =
                runCatching {
                    ReportClient.parseStatuses(reply.body)
                }.getOrDefault(emptyList()).associateBy { it.optString("id") }
            mutate { list ->
                list.mapNotNull { report ->
                    val json = byId[report.id] ?: return@mapNotNull report
                    val wire = json.optString("status")
                    if (wire == "gone") return@mapNotNull null
                    val note = report.noteOf(json)
                    report.copy(
                        short = json.optStringOrNull("short") ?: report.short,
                        status = ReportStatus.of(wire) ?: report.status,
                        fixedIn = json.optStringOrNull("fixedIn"),
                        fixLine = json.optStringOrNull("fixLine"),
                        issue = json.optIntOrNull("issue"),
                        note = note,
                        noteSeen = report.noteSeen && note == report.note,
                        events =
                            ReportEvent
                                .list(json.optJSONArray("events"), report::noteOf)
                                .ifEmpty { report.events },
                    )
                }
            }
        }
    }

    private val pendingDeletes = ConcurrentHashMap<String, Job>()
    private val pendingGroups = ConcurrentHashMap<String, List<SentReport>>()

    fun delete(report: SentReport) {
        val group = listOf(report) + _reports.value.filter { it.replyTo == report.id }
        val ids = group.map { it.id }.toSet()
        mutate { list -> list.filterNot { it.id in ids } }
        pendingGroups[report.id] = group
        pendingDeletes[report.id] =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                if (!pendingDeletes.remove(report.id, coroutineContext.job)) return@launch
                pendingGroups.remove(report.id)
                group.forEach { ledger.deletePayload(it.id) }
                if (report.status != ReportStatus.WAITING &&
                    report.status != ReportStatus.REJECTED
                ) {
                    ReportClient.delete(report.id, report.deleteToken)
                }
            }
    }

    fun restore(report: SentReport) {
        val pending = pendingDeletes.remove(report.id) ?: return
        pending.cancel()
        val group = pendingGroups.remove(report.id) ?: listOf(report)
        mutate { list -> (list + group).sortedByDescending { it.sentAt } }
    }

    fun checkRelease(force: Boolean) {
        if (!force && !_autoCheckUpdates.value) return
        val last = prefs.getLong(RELEASE_CHECKED_AT, 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        if (!force && System.currentTimeMillis() < prefs.getLong(RELEASE_RETRY_AT, 0)) return
        if (_release.value is ReleaseCheck.Checking) return
        val previous = _release.value
        _release.value = ReleaseCheck.Checking(previous.known)
        scope.launch {
            _release.value =
                when (val reply = fetchRelease()) {
                    is ApiResponse.Ok -> {
                        val release = runCatching { Release.parse(reply.body) }.getOrNull()
                        val now = System.currentTimeMillis()
                        prefs.edit {
                            putString(RELEASE_BODY, reply.body)
                            putLong(RELEASE_CHECKED_AT, now)
                            remove(RELEASE_RETRY_AT)
                        }
                        ReleaseCheck.Done(release, now)
                    }

                    else -> {
                        Log.w(
                            ModuleConstants.LOG_TAG,
                            "release check failed source=github reason=$reply",
                        )
                        (reply as? ApiResponse.Retryable)?.afterSeconds?.let { seconds ->
                            prefs.edit {
                                putLong(
                                    RELEASE_RETRY_AT,
                                    System.currentTimeMillis() + seconds * MILLIS,
                                )
                            }
                        }
                        if (force) {
                            ReleaseCheck.Failed(last.takeIf { it > 0 }, previous.known)
                        } else {
                            previous.takeUnless { it is ReleaseCheck.Checking }
                                ?: ReleaseCheck.Idle
                        }
                    }
                }
        }
    }

    private fun fetchRelease(): ApiResponse {
        val worker = ReportClient.release()
        if (worker is ApiResponse.Ok) {
            prefs.edit { remove(GITHUB_ETAG) }
            return worker
        }
        Log.w(ModuleConstants.LOG_TAG, "release check failed source=worker reason=$worker")
        val cached = prefs.getString(RELEASE_BODY, null)
        val etag = prefs.getString(GITHUB_ETAG, null)?.takeIf { cached != null }
        return when (val gitHub = ReportClient.gitHubRelease(etag)) {
            is ApiResponse.Ok if gitHub.code == HttpURLConnection.HTTP_NOT_MODIFIED &&
                cached != null -> {
                ApiResponse.Ok(gitHub.code, cached)
            }

            is ApiResponse.Ok -> {
                runCatching {
                    ApiResponse.Ok(
                        gitHub.code,
                        Release.fromGitHub(gitHub.body, ReportConfig.assetPattern),
                    )
                }.onSuccess {
                    gitHub.etag?.let { etag -> prefs.edit { putString(GITHUB_ETAG, etag) } }
                }.getOrElse {
                    ApiResponse.Refused(
                        gitHub.code,
                        it.message ?: "bad release json",
                    )
                }
            }

            else -> {
                gitHub
            }
        }
    }

    fun startInstall(release: Release) {
        val asset = release.asset ?: return
        installJob?.cancel()
        installJob =
            scope.launch {
                val job = coroutineContext.job
                _install.value = InstallStep.Downloading(0, asset.size, "")
                val downloaded =
                    runCatching {
                        updater.download(asset, { !job.isActive }) {
                            if (job.isActive) {
                                _install.value =
                                    it
                            }
                        }
                    }.getOrElse {
                        if (!job.isActive) return@launch
                        val bytes = (_install.value as? InstallStep.Downloading)?.bytes ?: 0
                        _install.value =
                            InstallStep.DownloadFailed(bytes, it.message ?: it.javaClass.simpleName)
                        return@launch
                    }
                val (file, hash) = downloaded
                val rejection =
                    verify(file, hash, asset.sha256) {
                        if (job.isActive) {
                            _install.value =
                                it
                        }
                    }
                if (!job.isActive) return@launch
                if (rejection != null) {
                    if (rejection.releaseKey) {
                        verifiedApk = file
                        verifiedVersion = release.versionCode
                    } else {
                        file.delete()
                    }
                    _install.value = rejection
                    return@launch
                }
                verifiedApk = file
                verifiedVersion = release.versionCode
                installVerified(file)
            }
    }

    private fun verify(
        file: File,
        hash: String,
        expected: String?,
        onStep: (InstallStep) -> Unit,
    ): InstallStep.Rejected? {
        expected ?: return InstallStep.Rejected(InstallStep.Rejected.Cause.NO_CHECKSUM, null, hash)
        val installed = updater.installedSigners()
        onStep(InstallStep.Verifying(expected, hash, installed.firstOrNull(), null))
        if (!expected.equals(hash, ignoreCase = true)) {
            return InstallStep.Rejected(InstallStep.Rejected.Cause.CHECKSUM, expected, hash)
        }
        val archive = updater.archiveInfo(file)
        val fileSigners = updater.signers(archive)
        onStep(
            InstallStep.Verifying(
                expected,
                hash,
                installed.firstOrNull(),
                fileSigners.firstOrNull(),
            ),
        )
        return when {
            archive?.packageName != context.packageName -> {
                InstallStep.Rejected(
                    InstallStep.Rejected.Cause.PACKAGE,
                    context.packageName,
                    archive?.packageName,
                )
            }

            fileSigners.isEmpty() || fileSigners != installed -> {
                InstallStep.Rejected(
                    InstallStep.Rejected.Cause.SIGNER,
                    installed.firstOrNull(),
                    fileSigners.firstOrNull(),
                    checksum = hash,
                )
            }

            else -> {
                null
            }
        }
    }

    private var verifiedApk: File? = null
    private var verifiedVersion: Int? = null

    private fun installVerified(file: File) {
        _install.value = InstallStep.Installing
        when (val root = Root.installApk(file)) {
            Root.Install.Done -> {
                return
            }

            is Root.Install.Failed -> {
                _install.value = InstallStep.InstallFailed(root.message)
            }

            Root.Install.Denied -> {
                if (!updater.canRequestInstalls()) {
                    _install.value = InstallStep.NeedsPermission
                } else {
                    runCatching { updater.install(file) { _install.value = it } }
                        .onFailure {
                            _install.value =
                                InstallStep.InstallFailed(it.message ?: it.javaClass.simpleName)
                        }
                }
            }
        }
    }

    fun installAnyway(release: Release) {
        val file = verifiedApk?.takeIf { verifiedVersion == release.versionCode } ?: return
        scope.launch { installVerified(file) }
    }

    fun resumeAfterInstallPermission() {
        val file = verifiedApk ?: return
        if (_install.value is InstallStep.NeedsPermission && updater.canRequestInstalls()) {
            scope.launch { installVerified(file) }
        }
    }

    private var installJob: Job? = null

    fun cancelInstall() {
        installJob?.cancel()
        _install.value = InstallStep.Idle
    }

    fun resetInstall() {
        if (_install.value !is InstallStep.Downloading &&
            _install.value !is InstallStep.Installing
        ) {
            _install.value = InstallStep.Idle
        }
    }

    companion object {
        private const val AUTO_CHECK_UPDATES = "autoCheckUpdates"
        private const val RELEASE_BODY = "releaseBody"
        private const val GITHUB_ETAG = "githubEtag"
        private const val RELEASE_CHECKED_AT = "releaseCheckedAt"
        private const val RELEASE_RETRY_AT = "releaseRetryAt"
        private const val POSTPONED_VERSION = "postponedVersion"
        private const val LAST_RUN_VERSION = "lastRunVersion"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val UNDO_WINDOW_MS = 6_000L
        private const val MILLIS = 1000L
    }
}
