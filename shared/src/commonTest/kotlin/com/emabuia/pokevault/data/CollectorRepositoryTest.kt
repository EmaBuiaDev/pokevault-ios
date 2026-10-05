package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.GoalCriteriaType
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Album e chase contro un Firestore finto: gli stessi documenti di Android. */
class CollectorRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-lab-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val commits = mutableListOf<JsonObject>()

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
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/albums") -> respond(ALBUMS, HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/goal_albums") -> respond(GOALS, HttpStatusCode.OK, json)
            path.endsWith(":commit") -> {
                commits += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.single().jsonObject
                respond("{}", HttpStatusCode.OK, json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun repository(): CollectorRepository {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        return CollectorRepository(FirestoreApi(client, "p"), auth, store, FirestoreWrites(client, "p"))
    }

    private fun JsonObject.fields() = this["update"]!!.jsonObject["fields"]!!.jsonObject
    private fun JsonObject.mask() = this["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun albumsAndChasesAreReadNewestFirst() = runTest {
        val repo = repository()
        val albums = repo.albums()
        assertEquals(listOf("Fuoco", "Preferite"), albums.map { it.name })
        assertEquals(listOf("doc-1", "doc-2"), albums.last().cardIds)
        assertEquals(18, albums.first().size)

        val goals = repo.goalAlbums()
        assertEquals(listOf("Buio Pesto", "Futuro"), goals.map { it.name })
        assertEquals("me05__ita", goals.first().criteriaValue)
        // Un criterio che questa versione non conosce non fa sparire il chase: vale come SET.
        assertEquals(GoalCriteriaType.SET, goals.last().criteria)
    }

    @Test
    fun aNewAlbumHasAndroidsFields() = runTest {
        repository().createAlbum(Album(name = "Fuoco", pokemonType = "Fuoco", size = 18, theme = "fire"))
        val fields = commits.single().fields()
        assertEquals(
            setOf("name", "description", "pokemonType", "expansion", "supertype", "size", "theme", "cardIds", "coverImageUrl", "createdAt"),
            fields.keys,
        )
        assertEquals("18", fields["size"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        assertTrue("timestampValue" in fields["createdAt"]!!.jsonObject)
    }

    @Test
    fun editingAnAlbumNeverTouchesCardsCoverOrDate() = runTest {
        repository().updateAlbum(Album(id = "a1", name = "Nuovo nome", size = 36))
        val write = commits.single()
        assertEquals(listOf("name", "description", "pokemonType", "expansion", "supertype", "size", "theme"), write.mask())
        assertFalse("cardIds" in write.fields())
    }

    @Test
    fun reorderingRewritesOnlyTheCardList() = runTest {
        repository().setAlbumCardIds("a1", listOf("doc-2", "doc-1"))
        val write = commits.single()
        assertEquals(listOf("cardIds"), write.mask())
        val ids = write.fields()["cardIds"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray
            .map { it.jsonObject["stringValue"]!!.jsonPrimitive.content }
        assertEquals(listOf("doc-2", "doc-1"), ids)
    }

    @Test
    fun aNewChaseKeepsItsTargetsFixed() = runTest {
        repository().createGoalAlbum(
            GoalAlbum(name = "Buio Pesto", criteriaType = "SET", criteriaValue = "me05__ita", targetCardApiIds = listOf("ita:me05:1", "ita:me05:2"))
        )
        val fields = commits.single().fields()
        assertEquals("SET", fields["criteriaType"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("me05__ita", fields["criteriaValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals(2, fields["targetCardApiIds"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray.size)
    }

    @Test
    fun everyWriteTellsTheOpenScreens() = runTest {
        val repo = repository()
        repo.addCardsToAlbum("a1", listOf("doc-3"))
        repo.deleteGoalAlbum("g1")
        assertEquals(2, repo.changes.value)
        assertTrue("appendMissingElements" in commits.first()["transform"]!!.jsonObject["fieldTransforms"]!!.jsonArray.single().jsonObject)
    }

    private companion object {
        const val ALBUMS = """
            {"documents":[
              {"name":"x/albums/a1","fields":{"name":{"stringValue":"Preferite"},"size":{"integerValue":"9"},
                "cardIds":{"arrayValue":{"values":[{"stringValue":"doc-1"},{"stringValue":"doc-2"}]}},
                "createdAt":{"timestampValue":"2026-01-01T10:00:00Z"}}},
              {"name":"x/albums/a2","fields":{"name":{"stringValue":"Fuoco"},"size":{"integerValue":"18"},
                "createdAt":{"timestampValue":"2026-09-01T10:00:00Z"}}}
            ]}
        """
        const val GOALS = """
            {"documents":[
              {"name":"x/goal_albums/g1","fields":{"name":{"stringValue":"Buio Pesto"},"criteriaType":{"stringValue":"SET"},
                "criteriaValue":{"stringValue":"me05__ita"},"targetCardApiIds":{"arrayValue":{"values":[{"stringValue":"ita:me05:1"}]}},
                "createdAt":{"timestampValue":"2026-09-01T10:00:00Z"}}},
              {"name":"x/goal_albums/g2","fields":{"name":{"stringValue":"Futuro"},"criteriaType":{"stringValue":"ILLUSTRATOR"},
                "createdAt":{"timestampValue":"2026-01-01T10:00:00Z"}}}
            ]}
        """
    }
}
