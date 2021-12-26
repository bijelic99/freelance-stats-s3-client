package com.freelanceStats.s3Client

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.freelanceStats.s3Client.models.FileReference

import scala.concurrent.Future

trait S3Client {
  def get(
      fileReference: FileReference
  ): Future[Option[(FileReference, Source[ByteString, _])]]
  def put(
      fileReference: FileReference,
      file: Source[ByteString, _]
  ): Future[FileReference]
  //def delete(fileReference: FileReference): Future[Done]
}
