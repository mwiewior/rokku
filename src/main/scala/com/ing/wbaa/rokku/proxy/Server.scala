package com.ing.wbaa.rokku.proxy

import akka.actor.{ ActorSystem, Props }
import com.ing.wbaa.rokku.proxy.config.{ HttpSettings, KafkaSettings, KerberosSettings, RangerSettings, StorageS3Settings, StsSettings }
import com.ing.wbaa.rokku.proxy.handler.{ FilterRecursiveListBucketHandler, RequestHandlerS3 }
import com.ing.wbaa.rokku.proxy.persistence.LineageRecorder
import com.ing.wbaa.rokku.proxy.provider.{ AuthenticationProviderSTS, AuthorizationProviderRanger, KerberosLoginProvider, LineageProviderAtlas, MessageProviderKafka, SignatureProviderAws }

object Server extends App {

  new RokkuS3Proxy with AuthorizationProviderRanger with RequestHandlerS3 with AuthenticationProviderSTS with LineageProviderAtlas with SignatureProviderAws with KerberosLoginProvider with FilterRecursiveListBucketHandler with MessageProviderKafka {

    override implicit lazy val system: ActorSystem = ActorSystem.create("rokku")

    override def kerberosSettings: KerberosSettings = KerberosSettings(system)

    override val httpSettings: HttpSettings = HttpSettings(system)
    override val rangerSettings: RangerSettings = RangerSettings(system)
    override val storageS3Settings: StorageS3Settings = StorageS3Settings(system)
    override val stsSettings: StsSettings = StsSettings(system)
    override val kafkaSettings: KafkaSettings = KafkaSettings(system)

    // start lineage recorder actor
    val requestRecorderRef = system.actorOf(Props(classOf[LineageRecorder], "lineage-rec-id-1"), "lineage-rec-id-1")

    // Force Ranger plugin to initialise on startup
    rangerPluginForceInit
  }.startup

}
