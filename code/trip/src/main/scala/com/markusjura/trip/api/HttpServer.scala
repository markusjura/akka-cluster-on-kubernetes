package com.markusjura.trip.api
import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.{Promise}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object HttpServer {

  private final case object BindFailure extends Reason
}

class HttpServer(implicit system: ActorSystem) extends Logging with Directives {

  import HttpServer._

  private implicit val mat = ActorMaterializer()
  private implicit val ec  = system.dispatcher

  private val address = "0.0.0.0"
  private val port    = 8080

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
          // No new connections are accepted
          // Existing connections are still allowed to perform request/response cycles
          binding.unbind()
        }
        shutdown.addTask(PhaseServiceRequestsDone, "api.requests-done") { () =>
          // Wait 5 seconds until all HTTP requests have been processed
          val p = Promise[Done]()
          system.scheduler.scheduleOnce(5.seconds) {
            p.success(Done)
          }
          p.future
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
