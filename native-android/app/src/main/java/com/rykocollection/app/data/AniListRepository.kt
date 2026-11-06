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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable data class AniListGraphQLRequest(val query: String, val variables: JsonObject)
@Serializable data class AniListEnvelope(val data: AniListData? = null, val errors: List<AniListError> = emptyList())
@Serializable data class AniListError(val message: String = "AniList request failed")
@Serializable data class AniListData(val Page: AniListPage? = null, val Media: AniListDetail? = null)
@Serializable data class AniListPage(val media: List<AniListMedia> = emptyList(), val airingSchedules: List<AniListAiring> = emptyList())
@Serializable data class AniListAiring(val airingAt: Long = 0, val episode: Int = 0, val media: AniListMedia)
@Serializable data class AniListMedia(val id: Int, val title: AniListTitle, val coverImage: AniListCover? = null, val bannerImage: String? = null, val averageScore: Int? = null, val format: String? = null, val seasonYear: Int? = null, val description: String? = null, val episodes: Int? = null, val chapters: Int? = null, val status: String? = null, val genres: List<String> = emptyList(), val countryOfOrigin: String? = null, val source: String? = null, val studios: AniListStudios? = null, val characters: AniListCharacters? = null, val trailer: AniListTrailer? = null)
@Serializable data class AniListTrailer(val id: String? = null, val site: String? = null, val thumbnail: String? = null)
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
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun trendingMedia(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}type: MediaType!) {
              Page(page: 1, perPage: 18) {
                media(type: ${'$'}type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }
        return request(query, variables).data?.Page?.media.orEmpty()
    }


    suspend fun featuredMedia(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${' {
              Page(page: 1, perPage: 5) {
                media(type: ${' sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query($type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query {
              Media(id: $id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 5) {
                media(type: $type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${' {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query($type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: ${' sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 5) {
                media(type: $type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${' {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 5) {
                media(type: ${' sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${' {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } bannerImage
                  averageScore format seasonYear description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun popularRecommendations(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${' {
              Page(page: 1, perPage: 12) {
                media(type: ${' sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 12) {
                media(type: ${' sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
}type, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject { put("type", if (manga) "MANGA" else "ANIME") }).data?.Page?.media.orEmpty()
    }

    suspend fun latestAiringEpisodes(): List<AniListAiring> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                airingSchedules(notYetAired: false, sort: TIME_DESC) {
                  airingAt episode
                  media { id title { english romaji native } coverImage { large extraLarge } format seasonYear }
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.airingSchedules.orEmpty()
    }

    suspend fun recentlyUpdatedManga(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 12) {
                media(type: MANGA, sort: UPDATED_AT_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge } format chapters status
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
    }

    suspend fun popularVideoTrailers(): List<AniListMedia> {
        val query = """
            query {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  trailer { id site thumbnail }
                  averageScore
                }
              }
            }
        """.trimIndent()
        return request(query, buildJsonObject {}).data?.Page?.media.orEmpty()
            .filter { it.trailer?.id?.isNotBlank() == true && it.trailer.site.equals("youtube", true) }
    }

    suspend fun searchMedia(term: String, manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${'$'}search: String!, ${'$'}type: MediaType!, ${'$'}page: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH, isAdult: false) {
                  id title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore format seasonYear
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("search", term.trim())
            put("type", if (manga) "MANGA" else "ANIME")
            put("page", 1)
        }
        return request(query, variables).data?.Page?.media.orEmpty()
    }

    suspend fun getMedia(id: Int): AniListDetail? {
        val query = """
            query(${'$'}id: Int!) {
              Media(id: ${'$'}id) {
                id title { english romaji native }
                coverImage { large extraLarge } bannerImage
                averageScore format seasonYear description(asHtml: false)
                episodes chapters status genres countryOfOrigin source
                studios(isMain: true) { nodes { name } }
                characters(page: 1, perPage: 20, sort: ROLE) {
                  edges { role node { name { full } image { large medium } } }
                }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("id", id) }
        return request(query, variables).data?.Media
    }

    private suspend fun request(query: String, variables: JsonObject): AniListEnvelope {
        val result: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQLRequest(query, variables))
        }.body()
        if (result.errors.isNotEmpty() && result.data == null) {
            throw IllegalStateException(result.errors.joinToString(" · ") { it.message })
        }
        return result
    }

    fun close() = client.close()
}
