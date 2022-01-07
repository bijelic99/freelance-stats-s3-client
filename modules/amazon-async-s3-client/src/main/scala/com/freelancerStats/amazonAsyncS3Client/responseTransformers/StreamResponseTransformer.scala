package com.freelancerStats.amazonAsyncS3Client.responseTransformers

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.freelancerStats.amazonAsyncS3Client.responseTransformers.StreamResponseTransformer.{
  ResponseSource,
  ResponseSourceAggregator
}
import software.amazon.awssdk.core.async.{
  AsyncResponseTransformer,
  SdkPublisher
}

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import scala.jdk.FutureConverters._

case class StreamResponseTransformer[ResponseT](
    actorSystem: ActorSystem
)(implicit
    executionContext: ExecutionContext
) extends AsyncResponseTransformer[ResponseT, ResponseSource[ResponseT]] {

  val actor: ResponseSourceAggregator[ResponseT] =
    new ResponseSourceAggregator[ResponseT]

  val actorRef: ActorRef[actor.Command] = actorSystem.spawnAnonymous(actor())

  override def prepare(): CompletableFuture[ResponseSource[ResponseT]] =
    actorRef
      .ask[Try[ResponseSource[ResponseT]]](actor.GetResponse)(
        10.seconds,
        actorSystem.toTyped.scheduler
      )
      .map {
        case Failure(exception) => throw exception
        case Success(response)  => response
      }
      .asJava
      .toCompletableFuture

  override def onResponse(response: ResponseT): Unit =
    actorRef ! actor.SetResponse(response)

  override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit =
    actorRef ! actor.SetSource(
      Source.fromPublisher(publisher).map(ByteString.apply)
    )

  override def exceptionOccurred(error: Throwable): Unit =
    actorRef ! actor.HandleError(error)
}

object StreamResponseTransformer {

  case class ResponseSource[ResponseT](
      response: ResponseT,
      source: Source[ByteString, NotUsed]
  )

  class ResponseSourceAggregator[ResponseT] {

    def apply(): Behavior[Command] =
      Behaviors.supervise(stopped()).onFailure(SupervisorStrategy.restart)

    private def stopped(): Behavior[Command] =
      Behaviors.receiveMessagePartial[Command] { case GetResponse(replyTo) =>
        running(replyTo)()
      }

    private def running(
        replyTo: ActorRef[Try[ResponseSource[ResponseT]]]
    )(
        maybeResponse: Option[ResponseT] = None,
        maybeSource: Option[Source[ByteString, NotUsed]] = None
    ): Behavior[Command] = Behaviors.receiveMessagePartial[Command] {
      case SetResponse(response) =>
        maybeSource.fold(running(replyTo)(maybeResponse = Some(response))) {
          source =>
            respond(replyTo)(Success(ResponseSource(response, source)))
            stopped()
        }
      case SetSource(source) =>
        maybeResponse.fold(running(replyTo)(maybeSource = Some(source))) {
          response =>
            respond(replyTo)(Success(ResponseSource(response, source)))
            stopped()
        }
      case HandleError(throwable) =>
        respond(replyTo)(Failure(throwable))
        stopped()
    }

    private def respond(replyTo: ActorRef[Try[ResponseSource[ResponseT]]])(
        message: Try[ResponseSource[ResponseT]]
    ): Unit =
      replyTo ! message

    sealed trait Command
    case class GetResponse(replyTo: ActorRef[Try[ResponseSource[ResponseT]]])
        extends Command
    case class SetResponse(response: ResponseT) extends Command
    case class SetSource(source: Source[ByteString, NotUsed]) extends Command
    case class HandleError(throwable: Throwable) extends Command
  }
}
