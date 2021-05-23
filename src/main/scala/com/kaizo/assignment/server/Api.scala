package com.kaizo.assignment.server

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.kaizo.assignment.actors.SupervisorActor.{ActiveClients, CreateCustomerStream, QueryLag, Response, StopClientStream}
import com.kaizo.assignment.actors.RestClientActor.StreamState
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class Api(backEndSuperVisor: ActorRef)(implicit system: ActorSystem, materializer: ActorMaterializer) {

  implicit val timeout: Timeout = Timeout(1 second)

  val startPath: Route = path("kaizo" / "v1"/ "addcustomerstream") {
    parameters("clientName", "token") { (clientName: String, token: String) =>
      val callback = backEndSuperVisor ? CreateCustomerStream(clientName, token)
      onComplete(callback){
        case Success(response: Response) => complete(HttpEntity(ContentTypes.`application/json`, response.toJson.toString))
        case Failure(_) => complete(StatusCodes.custom(500, s"Something went wrong when creating the stream"))
      }
    }
  } ~ path("kaizo" / "v1"/ "streamlag"){
    parameters("clientName"){ (clientName: String) =>
      val callback = backEndSuperVisor ? QueryLag(clientName)
      onComplete(callback){
        case Success(state: StreamState) => complete(HttpEntity(ContentTypes.`application/json`, state.toJson.toString))
        case Failure(_) => complete(StatusCodes.custom(404, s"No Client found with id: $clientName"))
      }
    }
  } ~ path("kaizo" / "v1"/ "activeclients"){
    val callback = backEndSuperVisor ? ActiveClients
    onComplete(callback){
      case Success(clients: Response) => complete(HttpEntity(ContentTypes.`application/json`, clients.toJson.toString))
      case Failure(_) => complete(StatusCodes.custom(404, s"No active clients"))
    }
  } ~ path("kaizo" / "v1"/ "stopclientstream"){
    parameters("clientName"){ (clientName: String) =>
      val callback = backEndSuperVisor ? StopClientStream(clientName)
      onComplete(callback){
        case Success(message: Response) => complete(HttpEntity(ContentTypes.`application/json`, message.toJson.toString))
        case Failure(_) => complete(StatusCodes.custom(404, s"No Client found with id: $clientName"))
      }
    }
  }
}

