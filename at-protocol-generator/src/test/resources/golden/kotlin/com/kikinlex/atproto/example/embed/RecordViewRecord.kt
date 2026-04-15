package com.kikinlex.atproto.example.embed

import com.kikinlex.atproto.example.actor.ProfileViewBasic
import com.kikinlex.atproto.runtime.AtField
import com.kikinlex.atproto.runtime.AtUri
import com.kikinlex.atproto.runtime.Cid
import com.kikinlex.atproto.runtime.Datetime
import kotlin.OptIn
import kotlin.collections.List
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class RecordViewRecord(
  public val author: ProfileViewBasic,
  public val cid: Cid,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val embeds: AtField<List<RecordViewRecordEmbedsUnion>> = AtField.Missing,
  public val indexedAt: Datetime,
  public val uri: AtUri,
  public val `value`: JsonObject,
) : RecordViewRecordUnion
