package com.ing.wbaa.rokku.proxy.provider

import akka.Done
import akka.http.scaladsl.model.{ HttpMethod, HttpMethods }
import com.ing.wbaa.rokku.proxy.data.{ AWSMessageEventJsonSupport, RequestId, S3Request }
import com.ing.wbaa.rokku.proxy.provider.aws.{ s3ObjectCreated, s3ObjectRemoved }
import com.ing.wbaa.rokku.proxy.provider.kafka.EventProducer

import scala.concurrent.Future

trait MessageProviderKafka extends EventProducer with AWSMessageEventJsonSupport {

  def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String)(implicit id: RequestId): Future[Done] =
    method match {
      case HttpMethods.POST | HttpMethods.PUT =>
        prepareAWSMessage(s3Request, method, principalId, s3Request.clientIPAddress, s3ObjectCreated(method.value))
          .map(jse =>
            sendSingleMessage(jse.toString(), kafkaSettings.createEventsTopic))
          .getOrElse(Future(Done))

      case HttpMethods.DELETE =>
        prepareAWSMessage(s3Request, method, principalId, s3Request.clientIPAddress, s3ObjectRemoved(method.value))
          .map(jse =>
            sendSingleMessage(jse.toString(), kafkaSettings.deleteEventsTopic))
          .getOrElse(Future(Done))

      case _ => Future(Done)
    }

}
