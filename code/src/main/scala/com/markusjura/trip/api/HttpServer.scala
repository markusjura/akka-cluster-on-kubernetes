package com.markusjura.trip.api
import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceUnbind, Reason}
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import org.apache.logging.log4j.scala.Logging

import scala.util.{Failure, Success}
import scala.concurrent.duration._

object HttpServer {

  final case class Config(address: String, port: Int, terminationDeadline: FiniteDuration)

  private final case object BindFailure extends Reason
}

class HttpServer(config: HttpServer.Config)(implicit system: ActorSystem) extends Logging with Directives {

  import HttpServer._
  import config._

  private implicit val mat = ActorMaterializer()
  private implicit val ec  = system.dispatcher

  private val shutdown = CoordinatedShutdown(system)

  Http()
    .bindAndHandle(route, address, port)
    .onComplete {
      case Failure(_) =>
        logger.error(s"Shutting down because cannot bind to $address:$port")
        shutdown.run(BindFailure)

      case Success(binding) =>
        logger.info(s"Listening for HTTP connections on ${binding.localAddress}")
        shutdown.addTask(PhaseServiceUnbind, "api.unbind") { () =>
          binding.unbind()
        }

        shutdown.addTask(PhaseServiceUnbind, "api.terminate") { () =>
          binding.terminate(terminationDeadline).map(_ => Done)
        }
    }

  private val tripController   = new TripController()
  private val systemController = new SystemController()

  private def route =
    // format: off
    pathPrefix("trip")(tripController.route) ~
    pathPrefix("system")(systemController.route)
    // format: on
}
