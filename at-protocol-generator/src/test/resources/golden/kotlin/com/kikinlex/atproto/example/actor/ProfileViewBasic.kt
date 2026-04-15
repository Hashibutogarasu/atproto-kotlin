package com.kikinlex.atproto.example.actor

import com.kikinlex.atproto.runtime.Did
import com.kikinlex.atproto.runtime.Handle
import kotlinx.serialization.Serializable

@Serializable
public data class ProfileViewBasic(
  public val did: Did,
  public val handle: Handle,
)
