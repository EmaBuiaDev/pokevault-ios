package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La sezione illustratori contro un Worker e un Firestore finti. */
class IllustratorRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-illus-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val commits = mutableListOf<JsonObject>()
    private val requestedPaths = mutableListOf<String>()

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    private fun bodyText(request: HttpRequestData): String = when (val body = request.body) {
        is TextContent -> body.text
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> ""
    }

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        requestedPaths += path
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/v1/illustrators") -> respond(INDEX, HttpStatusCode.OK, json)
            path.endsWith("/v1/illustrators/K.%20Hoshiba/cards") -> respond(
                """{"illustrator":"K. Hoshiba","cards":[{"cardId":"SV1_IT_001.png","espansioneId":"sv1","nome":"Pikachu"},{"cardId":"","espansioneId":"sv1","nome":"rotta"}]}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/v1/illustrators/K%20Hoshiba/cards") -> respond(
                """{"illustrator":"K Hoshiba","cards":[{"cardId":"SV1_IT_001.png","espansioneId":"sv1","nome":"Pikachu"},{"cardId":"SV1_IT_002.png","espansioneId":"sv1","nome":"Raichu"}]}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1/followed_illustrators") -> respond(
                """{"documents":[{"name":"projects/p/databases/(default)/documents/users/uid-1/followed_illustrators/k hoshiba","fields":{}}]}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith(":commit") -> {
                commits += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.single().jsonObject
                respond("{}", HttpStatusCode.OK, json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun repository(): IllustratorRepository {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        return IllustratorRepository(client, store, auth, FirestoreApi(client, "p"), FirestoreWrites(client, "p"), baseUrl = "https://w")
    }

    @Test
    fun theIndexJoinsSpellingsAndCountsTheCatalogGap() = runTest {
        val index = repository().index()
        val hoshiba = index.entries.single { it.key == "k hoshiba" }
        assertEquals("K. Hoshiba", hoshiba.displayName)
        assertEquals(listOf("ita:sv1:1", "ita:sv2:5", "ita:sv1:2"), hoshiba.cardApiIds)
        // La voce senza carte non entra, come su Android.
        assertTrue(index.entries.none { it.key == "vuoto" })
        assertEquals(386, index.cardsWithoutIllustrator)
    }

    @Test
    fun theIndexIsReadOnceThenFromMemory() = runTest {
        val repo = repository()
        repo.index()
        repo.index()
        assertEquals(1, requestedPaths.count { it.endsWith("/v1/illustrators") })
    }

    @Test
    fun cardsComeFromEverySpellingWithoutDuplicates() = runTest {
        val repo = repository()
        val cards = repo.cards(repo.index().entries.single { it.key == "k hoshiba" })
        assertEquals(listOf("Pikachu", "Raichu"), cards.map { it.nome })
    }

    @Test
    fun followingWritesTheSameDocumentAsAndroid() = runTest {
        val repo = repository()
        assertEquals(setOf("k hoshiba"), repo.followed())

        repo.setFollowed("mitsuhiro arita", followed = true)
        val set = commits.single()["update"]!!.jsonObject
        assertTrue(set["name"]!!.jsonPrimitive.content.endsWith("/users/uid-1/followed_illustrators/mitsuhiro arita"))
        assertTrue("followedAt" in set["fields"]!!.jsonObject)

        repo.setFollowed("mitsuhiro arita", followed = false)
        assertTrue(commits.last()["delete"]!!.jsonPrimitive.content.endsWith("/followed_illustrators/mitsuhiro arita"))
        assertEquals(2, repo.followChanges.value)
    }

    private companion object {
        const val INDEX = """
            {"illustrators":[
              {"name":"K. Hoshiba","cardCount":2,"expansionCount":2,"cardIds":["SV1_IT_001.png","SV2_IT_5.png"]},
              {"name":"K Hoshiba","cardCount":1,"expansionCount":1,"cardIds":["SV1_IT_002.png"]},
              {"name":"Vuoto","cardCount":0,"expansionCount":0,"cardIds":[]}
            ],"cardsWithoutIllustrator":386}
        """
    }
}
