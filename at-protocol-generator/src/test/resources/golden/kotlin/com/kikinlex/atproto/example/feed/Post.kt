package com.kikinlex.atproto.example.feed

import com.kikinlex.atproto.runtime.AtField
import com.kikinlex.atproto.runtime.Datetime
import com.kikinlex.atproto.runtime.Language
import kotlin.OptIn
import kotlin.String
import kotlin.collections.List
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class Post(
  public val createdAt: Datetime,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val embed: AtField<PostEmbedUnion> = AtField.Missing,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val langs: AtField<List<Language>> = AtField.Missing,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val reply: AtField<PostReplyRef> = AtField.Missing,
  public val text: String,
)
