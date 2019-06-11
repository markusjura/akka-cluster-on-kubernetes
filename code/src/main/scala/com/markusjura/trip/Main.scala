package com.markusjura.trip

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.management.scaladsl.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.markusjura.trip.api.HttpServer
import org.apache.logging.log4j.scala.Logging
import pureconfig.generic.auto.exportReader
import pureconfig.loadConfigOrThrow

object Main extends Logging {

  final case class Config(http: HttpServer.Config)

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("trip")
    val cluster         = Cluster(system)

    val config = loadConfigOrThrow[Config]("trip")

    // Start application after self member joined the cluster (Up)
    cluster.registerOnMemberUp {
      new HttpServer(config.http)
    }

    bootstrapCluster(system, cluster)

    logger.info(s"${cluster.selfAddress} started and ready to join cluster")
  }

  private def bootstrapCluster(system: ActorSystem, cluster: Cluster): Unit =
    if (onKubernetes) {
      // Starting Akka Cluster Management endpoint
      AkkaManagement(system).start()
      // Initiating Akka Cluster Bootstrap procedure
      ClusterBootstrap(system).start()
    } else {
      cluster.join(cluster.selfAddress)
    }

  private def onKubernetes: Boolean =
    sys.env.contains("KUBERNETES_PORT")
}
