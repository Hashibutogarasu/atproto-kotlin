package io.github.kikin81.atproto.runtime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@PublishedApi
internal val recordDecoderJson: Json = Json { ignoreUnknownKeys = true }

public fun <T> JsonObject.decodeRecord(serializer: KSerializer<T>, json: Json = recordDecoderJson): T = json.decodeFromJsonElement(serializer, this)

public inline fun <reified T> JsonObject.decodeRecord(json: Json = recordDecoderJson): T = json.decodeFromJsonElement(this)
