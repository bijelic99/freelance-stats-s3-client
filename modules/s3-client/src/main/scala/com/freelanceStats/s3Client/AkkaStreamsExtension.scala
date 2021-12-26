package com.freelanceStats.s3Client

import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.freelanceStats.s3Client.models.FileReference

trait AkkaStreamsExtension { _: S3Client =>

  val getFlow
      : Flow[FileReference, Option[(FileReference, Source[ByteString, _])], _] =
    Flow[FileReference]
      .flatMapConcat(ref => Source.future(get(ref)))

  val putFlow: Flow[(FileReference, Source[ByteString, _]), FileReference, _] =
    Flow[(FileReference, Source[ByteString, _])]
      .flatMapConcat { case ref -> file => Source.future(put(ref, file)) }

//  val deleteFlow: Flow[FileReference, Done, _] =
//    Flow[FileReference]
//      .flatMapConcat(ref => Source.future(delete(ref)))

}
