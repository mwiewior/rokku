package com.ing.wbaa.rokku.proxy.persistence

import akka.actor.Actor
import akka.http.scaladsl.model.RemoteAddress
import akka.persistence.PersistentActor
import com.ing.wbaa.rokku.proxy.data.LineageLiterals.AWS_S3_OBJECT_TYPE
import com.ing.wbaa.rokku.proxy.data.{ AccessType, LineageHeaders, LineageObjectGuids, User }
import com.ing.wbaa.rokku.proxy.persistence.LineageRecorder.{ DeleteEntityLineageEvt, LineageForCopyOperationEvt, ReadOrWriteLineageEvt }
import com.typesafe.scalalogging.LazyLogging

object LineageRecorder {
  case class ReadOrWriteLineageEvt(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      clientIPAddress: RemoteAddress,
      externalFsPath: Option[String] = None,
      guids: LineageObjectGuids = LineageObjectGuids()
  )

  case class LineageForCopyOperationEvt(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      clientIPAddress: RemoteAddress,
      srcGuids: LineageObjectGuids = LineageObjectGuids(),
      destGuids: LineageObjectGuids = LineageObjectGuids()
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
