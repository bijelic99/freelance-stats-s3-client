package com.freelancerStats.amazonAsyncS3Client.configurations

trait S3ClientConfiguration {
  def accessKey: Option[String]
  def secretAccessKey: Option[String]
  def endpoint: Option[String]
  def region: Option[String]
}
