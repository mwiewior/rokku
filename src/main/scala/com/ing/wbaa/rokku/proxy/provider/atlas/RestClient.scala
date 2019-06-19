package com.ing.wbaa.rokku.proxy.provider.atlas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, StatusCodes }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.ing.wbaa.rokku.proxy.provider.atlas.RestClient.RestClientException
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }

case class EntitySearchResult(requestId: String, definition: JsObject)
case class EntityId(state: String, jsonClass: String, typeName: String, version: Int, id: String)

trait RestClient extends LazyLogging with DefaultJsonProtocol {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def ec: ExecutionContext
  protected[this] implicit def mat: Materializer

  // Entity search result json format
  implicit val idReader = jsonFormat5(EntityId)
  implicit val resultReader = jsonFormat2(EntitySearchResult)

  // test settings
  private val atlasBaseUri = "http://localhost:21000"
  private val atlasApiUriV1 = atlasBaseUri + "/api/atlas"
  private val authHeader = Authorization(BasicHttpCredentials("admin", "admin"))

  def getEntityGUID(typeName: String, value: String): Future[String] = {
    Http(system).singleRequest(HttpRequest(
      HttpMethods.GET,
      atlasApiUriV1 + s"/entities?type=${typeName}&property=qualifiedName&value=${value}"
    ).withHeaders(authHeader))
      .flatMap {
        case response if response.status == StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { jsonString =>
            val searchResult = jsonString.parseJson.convertTo[EntitySearchResult]
            logger.debug("Atlas RestClient: Extracted GUID: " + searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id)
            searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id
          }
        case response if response.status == StatusCodes.NotFound =>
          logger.debug("Atlas RestClient: Extracted GUID: entity not found")
          Future.successful((""))
        case response =>
          logger.debug(s"Atlas getEntityGUID failed: ${response.status}")
          Future.failed(RestClientException(s"Atlas getEntityGUID failed: ${response.status}"))
      }.recoverWith { case ex =>
        logger.debug(s"Atlas getEntityGUID failed: ${ex.getMessage}")
        Future.failed(RestClientException(s"Atlas getEntityGUID failed: ${ex.getMessage}", ex.getCause))
      }
  }
}

object RestClient {
  final case class RestClientException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
