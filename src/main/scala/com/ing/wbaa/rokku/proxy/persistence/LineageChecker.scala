package com.ing.wbaa.rokku.proxy.persistence

import akka.Done
import akka.actor.{ Actor, ActorSystem }
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.ing.wbaa.rokku.proxy.data.LineageLiterals.AWS_S3_OBJECT_TYPE
import com.ing.wbaa.rokku.proxy.persistence.LineageChecker.{ CheckerWakeUp, Offset, QueryStateRWLineage }
import com.ing.wbaa.rokku.proxy.persistence.LineageRecorder.ReadOrWriteLineageEvt
import com.ing.wbaa.rokku.proxy.provider.atlas.RestClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

object LineageChecker {

  case object QueryStateRWLineage
  case object CheckerWakeUp
  case class Offset(current: Long = 0L)

}

class LineageChecker(lineagePersistenceId: String) extends PersistentActor with LazyLogging with RestClient {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()
  //var startOffset = 0L
  private val CHECKER_PERSISTENCE_ID = "checker-1"

  override def receiveRecover: Receive = Actor.emptyBehavior

  override def persistenceId: String = CHECKER_PERSISTENCE_ID

  val queries = PersistenceQuery(context.system)
    .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  //todo: Retry to wait for atlas processing delay
  // stream can be processed continously, instead of materializing to seq
  override def receiveCommand: Receive = {
    case CheckerWakeUp =>
      logger.info(s"$CHECKER_PERSISTENCE_ID: starting...")
      val initialOffsetF = Future { persist(Offset(1L)) { o => logger.info(s"$CHECKER_PERSISTENCE_ID: Saving initial offset ${o.current}") }; Done }
      queries.currentEventsByPersistenceId(CHECKER_PERSISTENCE_ID, 0L, Long.MaxValue)
        .map(_.event)
        .runWith(Sink.last)
        .mapTo[Offset]
        .recoverWith { case e: Exception =>
          logger.info("Unable to get offset, reseting state: " + e.getMessage)
          initialOffsetF
        }

    case QueryStateRWLineage =>
      val lastStoredOffset = queries.currentEventsByPersistenceId(CHECKER_PERSISTENCE_ID, 0L, Long.MaxValue)
        .map(_.event)
        .runWith(Sink.last)
        .mapTo[Offset]

      val eventsF =
        lastStoredOffset.flatMap { o =>
          logger.info(s"$CHECKER_PERSISTENCE_ID: found offset ${o.current}")
          val evts = queries.currentEventsByPersistenceId(lineagePersistenceId, o.current, Long.MaxValue)
            .map(_.event)
            .runWith(Sink.seq)
            .mapTo[Seq[ReadOrWriteLineageEvt]]

          evts.map { s =>
            if (s.nonEmpty) {
              persist(Offset(o.current + 1)) { o => logger.info(s"$CHECKER_PERSISTENCE_ID: Saving new offset ${o.current}") }
            }
          }
          evts
        }

      eventsF.map(_.map { e =>
        val bucketObject = e.lh.bucketObject.getOrElse("")
        logger.info(s"$CHECKER_PERSISTENCE_ID: Checking lineage for event objects :  bucket -> ${e.lh.bucket} bucketObject -> $bucketObject")
        getEntityGUID(AWS_S3_OBJECT_TYPE, bucketObject).map(g => logger.info(s"Found lineage for $AWS_S3_OBJECT_TYPE: $bucketObject"))
      })

    case _ => logger.info(s"$CHECKER_PERSISTENCE_ID: Unknown query")
  }
}
