package com.kikinlex.atproto.example

import com.kikinlex.atproto.runtime.NoXrpcParams
import com.kikinlex.atproto.runtime.XrpcClient

public class ExampleService(
  private val client: XrpcClient,
) {
  public suspend fun sendShared(request: SendSharedRequest): SendSharedResponse = client.procedure(
      nsid = "example.sendShared",
      params = NoXrpcParams,
      paramsSerializer = NoXrpcParams.serializer(),
      input = request,
      inputSerializer = SendSharedRequest.serializer(),
      responseSerializer = SendSharedResponse.serializer(),
  )
}
