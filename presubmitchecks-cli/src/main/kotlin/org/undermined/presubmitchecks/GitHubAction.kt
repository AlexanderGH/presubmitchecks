package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.FileContents
import org.undermined.presubmitchecks.core.visit
import org.undermined.presubmitchecks.git.GitChangelists
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.InputStream

class GitHubAction : SuspendingCliktCommand("github-action") {
    val githubApiUrl by option(envvar = "GITHUB_API_URL").required()
    val githubRepoToken by option(envvar = "GITHUB_REPO_TOKEN").required()
    val githubEventPath by option(envvar = "GITHUB_EVENT_PATH").required()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun run() {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val jsonMediaType = MediaType.get("application/json; charset=UTF8")
        val retrofit = Retrofit.Builder()
            .baseUrl(githubApiUrl)
            .addConverterFactory(json.asConverterFactory(jsonMediaType))
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
            files = changedFiles.map {
                when (it.status) {
                    "added" -> Changelist.FileOperation.AddedFile(
                        it.filename,
                        afterRevision = contentsForFileRef(
                            event.pull_request,
                            it.filename,
                            event.pull_request.head.sha,
                            it.patch == null,
                        ),
                    )
                    "removed" -> Changelist.FileOperation.RemovedFile(
                        it.filename,
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

        val ifChangeThenChange = IfChangeThenChangeChecker()

        changelist.visit(listOf(ifChangeThenChange))

        val missingChanges = ifChangeThenChange.getMissingChanges()
        if (missingChanges.isNotEmpty()) {
            echo("Missing changes to these blocks:\n")
            missingChanges.forEach {
                echo(" $it\n")
            }
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