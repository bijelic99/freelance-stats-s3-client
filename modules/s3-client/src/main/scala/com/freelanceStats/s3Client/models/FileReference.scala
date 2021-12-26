package com.freelanceStats.s3Client.models

import org.joda.time.DateTime

case class FileReference(
    bucket: String,
    key: String,
    lastModified: Option[DateTime] = None,
    eTag: Option[String] = None,
    size: Option[Long] = None,
    contentType: Option[String] = None
)
