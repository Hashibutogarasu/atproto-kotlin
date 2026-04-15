package com.kikinlex.atproto.example.actor

import com.kikinlex.atproto.runtime.Did
import com.kikinlex.atproto.runtime.Handle
import kotlin.String
import kotlinx.serialization.Serializable

@Serializable
public data class ProfileView(
  public val description: String? = null,
  public val did: Did,
  public val displayName: String? = null,
  public val handle: Handle,
)
