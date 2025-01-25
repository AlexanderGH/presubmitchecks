package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType
import okhttp3.OkHttpClient
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultDebug
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckResultMessage.Severity
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerRegistry
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CheckerService
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileContents
import org.undermined.presubmitchecks.core.runChecks
import org.undermined.presubmitchecks.core.visit
import org.undermined.presubmitchecks.git.GitChangelists
import org.undermined.presubmitchecks.git.GitHubWorkflowCommands
import org.undermined.presubmitchecks.git.GitLocalRepository
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.InputStream

class GitHubAction : SuspendingCliktCommand("github-action") {
    val githubApiUrl by option(envvar = "GITHUB_API_URL").required()
    val githubRepoToken by option(envvar = "GITHUB_REPO_TOKEN").required()
    val githubEventPath by option(envvar = "GITHUB_EVENT_PATH").required()
    val config by option(envvar = "INPUT_CONFIG_FILE")
    val fix by option(help="Apply fixes to any changed files.")
        .boolean()
        .default(false)

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun run() {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val jsonMediaType = MediaType.get("application/json; charset=UTF8")
        val okHttpClient = OkHttpClient.Builder()
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(githubApiUrl)
            .addConverterFactory(json.asConverterFactory(jsonMediaType))
            .client(okHttpClient)
            .callbackExecutor(Dispatchers.IO.asExecutor())
            .build()
        val githubService = retrofit.create(GithubService::class.java)

        val event = File(githubEventPath).inputStream().use {
            json.decodeFromStream<GithubEvent>(it)
        }

        val changedFiles = githubService.getPullRequestFiles(
            "Bearer $githubRepoToken",
            event.repository.owner.login,
            event.repository.name,
            event.pull_request.number,
            1
        ).execute().body().orEmpty()

        val changelist = Changelist(
            title = event.pull_request.title,
            description = event.pull_request.body ?: "",
            patchOnly = false,
            files = changedFiles.map {
                when (it.status) {
                    "added" -> Changelist.FileOperation.AddedFile(
                        it.filename,
                        patchLines = it.patch?.let { patch ->
                            GitChangelists.parseFilePatch(patch)
                        } ?: emptyList(),
                        afterRef = event.pull_request.head.sha,
                        afterRevision = contentsForFileRef(
                            event.pull_request,
                            it.filename,
                            event.pull_request.head.sha,
                            it.patch == null,
                        ),
                    )

                    "removed" -> Changelist.FileOperation.RemovedFile(
                        it.filename,
                        patchLines = it.patch?.let { patch ->
                            GitChangelists.parseFilePatch(patch)
                        } ?: emptyList(),
                        beforeRef = event.pull_request.base.sha,
                        beforeRevision = contentsForFileRef(
                            event.pull_request,
                            it.filename,
                            event.pull_request.base.sha,
                            it.patch == null,
                        ),
                    )

                    "modified" -> if (it.patch == null) {
                        Changelist.FileOperation.ModifiedFile(
                            it.filename,
                            beforeName = it.previous_filename ?: it.filename,
                            patchLines = emptyList(),
                            beforeRef = event.pull_request.base.sha,
                            afterRef = event.pull_request.head.sha,
                            beforeRevision = contentsForFileRef(
                                event.pull_request,
                                it.filename,
                                event.pull_request.base.sha,
                                true,
                            ),
                            afterRevision = contentsForFileRef(
                                event.pull_request,
                                it.filename,
                                event.pull_request.head.sha,
                                true,
                            ),
                        )
                    } else {
                        Changelist.FileOperation.ModifiedFile(
                            it.filename,
                            beforeName = it.previous_filename ?: it.filename,
                            patchLines = GitChangelists.parseFilePatch(it.patch),
                            beforeRef = event.pull_request.base.sha,
                            afterRef = event.pull_request.head.sha,
                            afterRevision = contentsForFileRef(
                                event.pull_request,
                                it.filename,
                                event.pull_request.head.sha,
                                false,
                            ),
                        )
                    }

                    else -> TODO()
                }
            }
        )

        val globalConfig: CheckerService.GlobalConfig = config?.let { filePath ->
            File(filePath).takeIf { it.exists() }?.inputStream()?.use {
                Json.decodeFromStream(it)
            }
        } ?: CheckerService.GlobalConfig()
        val checkerService = CheckerRegistry.newServiceFromConfig(globalConfig)

        var hasFailure = false

        val reporter = object : CheckerReporter {
            override fun report(result: CheckResult) {
                when (result) {
                    is CheckResultMessage -> {
                        if (result.severity == Severity.ERROR) {
                            hasFailure = true
                        }
                        val location = result.location
                        GitHubWorkflowCommands.message(
                            severity = result.severity,
                            title = "${result.checkGroupId}: ${result.title}",
                            message = result.message,
                            file = location?.file.toString(),
                            line = location?.startLine,
                            endLine = location?.endLine,
                            col = location?.startCol,
                            endColumn = location?.endCol,
                        )
                    }

                    is CheckResultDebug -> {
                        GitHubWorkflowCommands.debug(result.message)
                    }
                }
            }

            override suspend fun flush() = Unit
        }

        try {
            val repository = GitLocalRepository(File(""))
            checkerService.runChecks(repository, changelist, reporter)
            reporter.flush()
        } finally {
            okHttpClient.dispatcher().executorService().shutdown()
            okHttpClient.connectionPool().evictAll()
        }

        if (hasFailure) {
            throw CliktError(statusCode = 1)
        }
    }

    private fun contentsForFileRef(
        pullRequest: GithubEvent.GithubPullRequest,
        filename: String,
        ref: String,
        isBinary: Boolean,
    ): FileContents {
        val rawFetcher: () -> InputStream = {
            if (File(".git").exists()) {
                if (ref == pullRequest.head.sha) {
                    File(filename).inputStream()
                } else {
                    Runtime.getRuntime().exec(arrayOf("git", "show", "$ref:$filename")).inputStream
                }
            } else {
                TODO()
            }
        }
        return if (isBinary) {
            FileContents.Binary(suspend {
                withContext(Dispatchers.IO) {
                    rawFetcher()
                }
            })
        } else {
            FileContents.Text(suspend {
                withContext(Dispatchers.IO) {
                    rawFetcher().reader().buffered(4096).lineSequence()
                }
            })
        }
    }

    interface GithubService {
        @GET("/repos/{owner}/{repo}/pulls/{pull_number}/files")
        fun getPullRequestFiles(
            @Header("Authorization") token: String,
            @Path("owner") owner: String,
            @Path("repo") repo: String,
            @Path("pull_number") number: Int,
            @Query("page") page: Int
        ): Call<List<GithubChangedFile>>

        @Serializable
        data class GithubChangedFile(
            val filename: String,
            val raw_url: String,
            val status: String,
            val patch: String? = null,
            val previous_filename: String? = null,
        )
    }

    @Serializable
    data class GithubEvent(
        val repository: GithubRepository,
        val pull_request: GithubPullRequest,
    ) {
        @Serializable
        data class GithubRepository(val name: String, val owner: GithubUser)

        @Serializable
        data class GithubPullRequest(
            val number: Int,
            val title: String,
            val body: String?,
            val changed_files: Int,
            val user: GithubUser,
            val head: GithubPullRequestRef,
            val base: GithubPullRequestRef,
        ) {
            @Serializable
            data class GithubPullRequestRef(val sha: String)
        }

        @Serializable
        data class GithubUser(val login: String)
    }
}
