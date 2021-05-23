package com.kaizo.assignment.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.kaizo.assignment.actors.RestClientActor.{AskState, StopStream}
import com.kaizo.assignment.actors.SupervisorActor.{ActiveClients, CreateCustomerStream, QueryLag, Response, StopClientStream}
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SupervisorActor(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext) extends Actor with ActorLogging {

  private var children = Map.empty[String, ActorRef]

  implicit val timeout: Timeout = Timeout(1 second)

  override def receive: Receive = {
    case CreateCustomerStream(name, token) =>
      createStream(name, token)
      sender() ! Response(s"Stream $name succesfully created")
    case QueryLag(id) =>
      val actor = children.get(id)
      actor match {
        case Some(actor) =>
          val state = actor ? AskState
          val orgSender = sender()
          state.onComplete {
            case Success(value) => orgSender ! value
            case Failure(ex) => println(ex)
          }
        case None => log.info("No actor found")
      }
    case ActiveClients => sender() ! Response(s"${children.keys.mkString(" ")}")
    case StopClientStream(id) =>
      stopStream(id)
      sender() ! Response(s"Stopped $id stream")
  }

  private def stopStream(id: String): Unit = {
    children(id) ! StopStream
    children -= id
  }

  private def createStream(name: String, token: String): Unit = {
    context.child(name).getOrElse{
      val stream = context.actorOf(RestClientActor.props(name, token), name)
      children += (name -> stream)
    }
  }

}

object SupervisorActor {

  def props(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext): Props = Props(new SupervisorActor())

  case class QueryLag(id: String)
  case class CreateCustomerStream(name: String, token: String)
  implicit val responseFormat: JsonFormat[Response] = jsonFormat1(Response)
  case class Response(message: String)
  case object ActiveClients
  case class StopClientStream(id: String)

}
