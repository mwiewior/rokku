package com.ing.wbaa.rokku.proxy.persistence

import akka.actor.{ Actor, ActorSystem }
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.ing.wbaa.rokku.proxy.persistence.LineageChecker.QueryStateRWLineage
import com.ing.wbaa.rokku.proxy.persistence.LineageRecorder.ReadOrWriteLineageEvt
import com.ing.wbaa.rokku.proxy.provider.atlas.RestClient
import com.ing.wbaa.rokku.proxy.data.LineageLiterals.AWS_S3_OBJECT_TYPE
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

object LineageChecker {

  case object QueryStateRWLineage

}

class LineageChecker(persistenceId: String) extends Actor with LazyLogging with RestClient {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()
  var startOffset = 0L

  //todo: Retry to wait for atlas processing delay
  // stream processing instead of materializing to seq
  override def receive: Receive = {
    case QueryStateRWLineage =>
      val queries = PersistenceQuery(context.system)
        .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

      val eventsF = queries.currentEventsByPersistenceId(persistenceId, startOffset, Long.MaxValue)
        .map(_.event)
        .runWith(Sink.seq)
        .mapTo[Seq[ReadOrWriteLineageEvt]]

      eventsF.map(_.map { e =>
        val bucketObject = e.lh.bucketObject.getOrElse("")
        logger.info(s"Checking lineage for event objects :  bucket -> ${e.lh.bucket} bucketObject -> $bucketObject")
        getEntityGUID(AWS_S3_OBJECT_TYPE, bucketObject).map(g => logger.info(s"Found lineage for $AWS_S3_OBJECT_TYPE: $bucketObject"))
        startOffset += 1
      })

    case _ => logger.info("Unknown query")
  }
}
