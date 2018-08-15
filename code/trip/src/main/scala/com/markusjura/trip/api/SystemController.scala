package com.markusjura.trip.api
import java.nio.file.{Files, Paths}

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import org.apache.logging.log4j.scala.Logging

class SystemController() extends Controller with Logging {

  override def route: Route =
    // format: off
    path("readiness") {
      get {
        complete(processReadiness)
      }
    } ~
    path("liveness") {
      get {
        complete(HttpResponse(StatusCodes.OK, entity = "I am alive"))
      }
    }
    // format: on

  private val shutdownIndicatorFilePath = Paths.get("/tmp/shutdown")

  private def processReadiness: HttpResponse =
    if (Files.exists(shutdownIndicatorFilePath)) {
      logger.info(s"Shutdown indicating file $shutdownIndicatorFilePath exists. Shutdown in progress")
      HttpResponse(StatusCodes.ServiceUnavailable, entity = "Shutdown in progress")
    } else {
      HttpResponse(StatusCodes.OK, entity = "Application startup completed")
    }
}
