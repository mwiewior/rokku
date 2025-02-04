package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.model.RemoteAddress.Unknown
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ HttpMethod, MediaType, MediaTypes, RemoteAddress }
import com.typesafe.scalalogging.LazyLogging

/**
 * @param credential
 * @param s3BucketPath     A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
 * @param s3Object
 * @param accessType The access type for this request, write includes actions like write/update/delete
 *
 */
case class S3Request(
    credential: AwsRequestCredential,
    s3BucketPath: Option[String],
    s3Object: Option[String],
    accessType: AccessType,
    clientIPAddress: RemoteAddress = Unknown,
    headerIPs: HeaderIPs = HeaderIPs(),
    mediaType: MediaType = MediaTypes.`text/plain`
)

object S3Request extends LazyLogging {
  def extractObject(pathString: String): Option[String] =
    if (pathString.endsWith("/") || pathString.split("/").length < 3) {
      None
    } else {
      Some(pathString.split("/").last)
    }

  def apply(credential: AwsRequestCredential, path: Path, httpMethod: HttpMethod,
      clientIPAddress: RemoteAddress, headerIPs: HeaderIPs, mediaType: MediaType): S3Request = {

    val pathString = path.toString()
    val s3path = if (path.length > 1) { Some(pathString) } else { None }
    val s3Object = extractObject(pathString)

    val accessType = httpMethod.value match {
      case "GET"    => Read(httpMethod.value)
      case "HEAD"   => Head(httpMethod.value)
      case "PUT"    => Write(httpMethod.value)
      case "POST"   => Write(httpMethod.value)
      case "DELETE" => Delete(httpMethod.value)
      case _ =>
        logger.debug("HttpMethod not supported")
        NoAccess
    }

    S3Request(credential, s3path, s3Object, accessType, clientIPAddress, headerIPs, mediaType)
  }
}
