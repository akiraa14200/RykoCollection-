package com.rykocollection.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class AniListGraphQLRequest(val query: String, val variables: Map<String, String>)
@Serializable data class AniListEnvelope(val data: AniListData? = null, val errors: List<AniListError> = emptyList())
@Serializable data class AniListError(val message: String = "AniList request failed")
@Serializable data class AniListData(val Page: AniListPage? = null, val Media: AniListDetail? = null)
@Serializable data class AniListPage(val media: List<AniListMedia> = emptyList())
@Serializable data class AniListMedia(val id: Int, val title: AniListTitle, val coverImage: AniListCover? = null, val bannerImage: String? = null, val averageScore: Int? = null, val format: String? = null, val seasonYear: Int? = null, val description: String? = null, val episodes: Int? = null, val chapters: Int? = null, val status: String? = null, val genres: List<String> = emptyList(), val countryOfOrigin: String? = null, val source: String? = null, val studios: AniListStudios? = null, val characters: AniListCharacters? = null)
@Serializable data class AniListDetail(val id: Int, val title: AniListTitle, val coverImage: AniListCover? = null, val bannerImage: String? = null, val averageScore: Int? = null, val format: String? = null, val seasonYear: Int? = null, val description: String? = null, val episodes: Int? = null, val chapters: Int? = null, val status: String? = null, val genres: List<String> = emptyList(), val countryOfOrigin: String? = null, val source: String? = null, val studios: AniListStudios? = null, val characters: AniListCharacters? = null)
@Serializable data class AniListTitle(val english: String? = null, val romaji: String? = null, val native: String? = null) { val display: String get() = english ?: romaji ?: native ?: "Untitled" }
@Serializable data class AniListCover(val large: String? = null, val extraLarge: String? = null) { val imageUrl: String? get() = extraLarge ?: large }
@Serializable data class AniListStudios(val nodes: List<AniListStudio> = emptyList())
@Serializable data class AniListStudio(val name: String = "")
@Serializable data class AniListCharacters(val edges: List<AniListCharacterEdge> = emptyList())
@Serializable data class AniListCharacterEdge(val role: String = "", val node: AniListCharacter)
@Serializable data class AniListCharacter(val name: AniListCharacterName, val image: AniListCharacterImage? = null)
@Serializable data class AniListCharacterName(val full: String = "")
@Serializable data class AniListCharacterImage(val large: String? = null, val medium: String? = null)

class AniListRepository {
    private val client = HttpClient(Android) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    suspend fun trendingMedia(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query($type: MediaType!) {
              Page(page: 1, perPage: 18) {
                media(type: $type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native } coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, mapOf("type" to if (manga) "MANGA" else "ANIME")).data?.Page?.media.orEmpty()
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query($search: String!, $type: MediaType!, $page: Int!) {
              Page(page: $page, perPage: 30) {
                media(search: $search, type: $type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native } coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, mapOf("search" to term.trim(), "type" to if (manga) "MANGA" else "ANIME", "page" to "1")).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query($id: Int!) {
              Media(id: $id) {
                id title { english romaji native } coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false) episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) { edges { role node { name { full } image { large medium } } } }
              }
            }
        """.trimIndent()
        return request(query, mapOf("id" to id.toString())).data?.Media
    }

    private suspend fun request(query: String, variables: Map<String, String>): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        return result
    }
    fun close() = client.close()
}
