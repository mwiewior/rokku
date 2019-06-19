package com.ing.wbaa.rokku.proxy.persistence

import akka.actor.Actor
import akka.http.scaladsl.model.{ HttpMethod, RemoteAddress }
import akka.persistence.PersistentActor
import com.ing.wbaa.rokku.proxy.data.LineageLiterals.AWS_S3_OBJECT_TYPE
import com.ing.wbaa.rokku.proxy.data.{ AccessType, LineageObjectGuids, User }
import com.ing.wbaa.rokku.proxy.persistence.LineageRecorder.{ DeleteEntityLineageEvt, LineageForCopyOperationEvt, ReadOrWriteLineageEvt }
import com.typesafe.scalalogging.LazyLogging

object LineageRecorder {
  case class CustomLineageHeaders(
      host: Option[String],
      bucket: String,
      pseduoDir: Option[String],
      bucketObject: Option[String],
      method: HttpMethod,
      contentType: String,
      clientType: Option[String],
      queryParams: Option[String],
      copySource: Option[String])

  case class ReadOrWriteLineageEvt(
      lh: CustomLineageHeaders,
      userSTS: User,
      //method: AccessType,
      clientIPAddress: RemoteAddress,
      externalFsPath: Option[String] = None,
      guids: LineageObjectGuids
  )

  case class LineageForCopyOperationEvt(
      lh: CustomLineageHeaders,
      userSTS: User,
      method: AccessType,
      clientIPAddress: RemoteAddress,
      srcGuids: LineageObjectGuids,
      destGuids: LineageObjectGuids
  )

  case class DeleteEntityLineageEvt(entityName: String, userSTS: User, entityType: String = AWS_S3_OBJECT_TYPE)
}

class LineageRecorder(id: String) extends PersistentActor with LazyLogging {
  override def persistenceId: String = id

  override def receiveRecover: Receive = Actor.emptyBehavior

  override def receiveCommand: Receive = {
    case msg: ReadOrWriteLineageEvt =>
      persist(msg) { m =>
        logger.info("Got request event to persist: " + m)
      }
    case msg: LineageForCopyOperationEvt =>

    case msg: DeleteEntityLineageEvt     =>

    case _                               => logger.debug("Unknown message, skipping persistance")
  }

}
