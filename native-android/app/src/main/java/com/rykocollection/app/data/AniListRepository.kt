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

@Serializable
data class AniListEnvelope(val data: AniListData? = null)

@Serializable
data class AniListData(val Page: AniListPage? = null)

@Serializable
data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle,
    val coverImage: AniListCover? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val seasonYear: Int? = null
)

@Serializable
data class AniListTitle(
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null
) {
    val display: String get() = english ?: romaji ?: native ?: "Untitled"
}

@Serializable
data class AniListCover(
    val large: String? = null,
    val extraLarge: String? = null
) {
    val imageUrl: String? get() = extraLarge ?: large
}

class AniListRepository {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun trendingMedia(manga: Boolean = false): List<AniListMedia> {
        val query = """
            query(${': MediaType!) {
              Page(page: 1, perPage: 18) {
                media(type: ${'$'}type, sort: TRENDING_DESC, isAdult: false) {
                  id
                  title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore
                  format
                  seasonYear
                }
              }
            }
        """.trimIndent()

        val response: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("query" to query, "variables" to mapOf("type" to if (manga) "MANGA" else "ANIME")))
        }.body()
        return response.data?.Page?.media.orEmpty()
    }

    fun close() = client.close()
}
}type: MediaType!) {
              Page(page: 1, perPage: 18) {
                media(type: ${', sort: TRENDING_DESC, isAdult: false) {
                  id
                  title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore
                  format
                  seasonYear
                }
              }
            }
        """.trimIndent()

        val response: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("query" to query, "variables" to mapOf("type" to if (manga) "MANGA" else "ANIME")))
        }.body()
        return response.data?.Page?.media.orEmpty()
    }

    fun close() = client.close()
}
}type, sort: TRENDING_DESC, isAdult: false) {
                  id
                  title { english romaji native }
                  coverImage { large extraLarge }
                  averageScore
                  format
                  seasonYear
                }
              }
            }
        """.trimIndent()

        val response: AniListEnvelope = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("query" to query, "variables" to mapOf("type" to if (manga) "MANGA" else "ANIME")))
        }.body()
        return response.data?.Page?.media.orEmpty()
    }

    fun close() = client.close()
}
