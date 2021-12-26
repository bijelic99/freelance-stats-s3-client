package com.freelancerStats.amazonAsyncS3Client.responseTransformers

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.freelancerStats.amazonAsyncS3Client.responseTransformers.StreamResponseTransformer.ResponseSource
import software.amazon.awssdk.core.async.{
  AsyncResponseTransformer,
  SdkPublisher
}

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class StreamResponseTransformer[ResponseT]
    extends AsyncResponseTransformer[ResponseT, ResponseSource[ResponseT]] {

  private var sourceCF: Option[CompletableFuture[Source[ByteString, NotUsed]]] =
    None
  private var response: Option[ResponseT] = None

  override def prepare(): CompletableFuture[ResponseSource[ResponseT]] = {
    sourceCF = Some(new CompletableFuture[Source[ByteString, NotUsed]]())
    sourceCF.get.thenApply(ResponseSource(response.get, _))
  }

  override def onResponse(response: ResponseT): Unit = {
    this.response = Some(response)
  }

  override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit =
    Source.fromPublisher(publisher).map(ByteString.apply)

  override def exceptionOccurred(error: Throwable): Unit =
    sourceCF.map(_.completeExceptionally(error))
}

object StreamResponseTransformer {
  def apply[ResponseT](): StreamResponseTransformer[ResponseT] =
    new StreamResponseTransformer[ResponseT]

  case class ResponseSource[ResponseT](
      response: ResponseT,
      source: Source[ByteString, NotUsed]
  )
}
