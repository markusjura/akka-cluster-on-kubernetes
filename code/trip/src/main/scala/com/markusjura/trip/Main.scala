package com.markusjura.trip

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.markusjura.trip.api.HttpServer
import org.apache.logging.log4j.scala.Logging

object Main extends Logging {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("trip")
    val cluster         = Cluster(system)

    // Start application after self member joined the cluster (Up)
    cluster.registerOnMemberUp {
      new HttpServer()
    }

    bootstrapCluster(system, cluster)

    logger.info(s"${cluster.selfAddress} started and ready to join cluster")
  }

  private def bootstrapCluster(system: ActorSystem, cluster: Cluster): Unit =
    if (onKubernetes) {
      // Starting Akka Cluster Management endpoint
      val akkaManagement = AkkaManagement(system)
      akkaManagement.start()
      // Initiating Akka Cluster Bootstrap procedure
      ClusterBootstrap(system).start()
    } else {
      cluster.join(cluster.selfAddress)
    }

  private def onKubernetes: Boolean =
    sys.env.contains("KUBERNETES_PORT")
}
