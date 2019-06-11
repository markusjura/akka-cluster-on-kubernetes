package com.markusjura.trip.api
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.markusjura.trip.api.model.Price
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.apache.logging.log4j.scala.Logging

object TripController {

  final case class CreateTripOffer(origin: String, destination: String)
  final case class TripOfferCreated(price: Price)
}

class TripController()(implicit system: ActorSystem) extends Controller with Logging {

  import TripController._
  import ErrorAccumulatingCirceSupport._
  import io.circe.generic.auto._

  private implicit val ec = system.dispatcher

  override def route: Route =
    path("offer") {
      post(processCreateTripOffer)
    }

  private def processCreateTripOffer =
    entity(as[CreateTripOffer]) { _ =>
      logger.info("Creating trip offer")
      complete(StatusCodes.OK -> TripOfferCreated(Price(amount = "10.00", currency = "EUR")))
    }
}
