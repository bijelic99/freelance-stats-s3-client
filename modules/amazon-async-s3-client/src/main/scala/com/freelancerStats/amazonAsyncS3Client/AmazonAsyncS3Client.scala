package com.freelancerStats.amazonAsyncS3Client

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.freelanceStats.s3Client.S3Client
import com.freelanceStats.s3Client.models.FileReference
import com.freelancerStats.amazonAsyncS3Client.configurations.S3ClientConfiguration
import com.freelancerStats.amazonAsyncS3Client.responseTransformers.StreamResponseTransformer
import com.freelancerStats.amazonAsyncS3Client.responseTransformers.StreamResponseTransformer.ResponseSource
import org.joda.time.DateTime
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  GetObjectResponse,
  NoSuchKeyException,
  PutObjectRequest
}

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining._
import scala.jdk.FutureConverters._

trait AmazonAsyncS3Client extends S3Client {

  implicit val executionContext: ExecutionContext
  implicit val actorSystem: ActorSystem
  implicit val materializer: Materializer

  def configuration: S3ClientConfiguration

  lazy val client: S3AsyncClient =
    S3AsyncClient
      .builder()
      .credentialsProvider(
        StaticCredentialsProvider
          .create(
            AwsBasicCredentials
              .create(
                configuration.accessKey.orNull,
                configuration.secretAccessKey.orNull
              )
          )
      )
      .pipe(b =>
        configuration.endpoint
          .fold(b)(endpoint => b.endpointOverride(new URI(endpoint)))
      )
      .pipe(b =>
        configuration.region.fold(b)(region => b.region(Region.of(region)))
      )
      .build()

  lazy val getObjectResponseTransformer
      : StreamResponseTransformer[GetObjectResponse] =
    StreamResponseTransformer[GetObjectResponse](actorSystem)

  override def get(
      fileReference: FileReference
  ): Future[Option[(FileReference, Source[ByteString, _])]] =
    client
      .getObject(
        GetObjectRequest
          .builder()
          .bucket(fileReference.bucket)
          .key(fileReference.key)
          .build(),
        getObjectResponseTransformer
      )
      .asScala
      .map { case ResponseSource(response, source) =>
        Some(
          fileReference
            .copy(
              lastModified =
                Some(new DateTime(response.lastModified().toEpochMilli)),
              eTag = Some(response.eTag()),
              size = Some(response.contentLength()),
              contentType = Some(response.contentType())
            ) -> source
        )
      }
      .recover {
        case t if t.getCause.isInstanceOf[NoSuchKeyException] =>
          None
      }

  override def put(
      fileReference: FileReference,
      file: Source[ByteString, _]
  ): Future[FileReference] = {
    lazy val fileByteArray =
      file.runWith(StreamConverters.asInputStream()).readAllBytes()
    client
      .putObject(
        PutObjectRequest
          .builder()
          .bucket(fileReference.bucket)
          .key(fileReference.key)
          .pipe(b =>
            fileReference.size
              .fold(b.contentLength(fileByteArray.length))(b.contentLength(_))
          )
          .pipe(b =>
            fileReference.contentType
              .fold(b)(b.contentType)
          )
          .build(),
        fileReference.size.fold(
          AsyncRequestBody.fromBytes(fileByteArray)
        )(_ =>
          AsyncRequestBody
            .fromPublisher(
              file
                .map(_.asByteBuffer)
                .toMat(Sink.asPublisher(false))(Keep.right)
                .run()
            )
        )
      )
      .asScala
      .map(response =>
        fileReference.copy(
          lastModified = Some(DateTime.now()),
          eTag = Some(response.eTag())
        )
      )
  }

}
