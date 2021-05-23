package com.kaizo.assignment.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.ResponseEntity
import akka.stream.ActorMaterializer
import com.kaizo.assignment.actors.JsonParserActor.{KillParser, ParserResponse, Payload}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.kaizo.assignment.actors.RestClientActor.{NoUpdate, State, UpdateState}

import scala.concurrent.ExecutionContext
import spray.json._
import spray.json.DefaultJsonProtocol._

class JsonParserActor(id: String)(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext)
  extends Actor  with ActorLogging {

  override def receive: Receive = {
    case Payload(res) =>
      val data = Unmarshaller.stringUnmarshaller(res)
      val orgSender = sender()
      data.onComplete{
        case Success(s) =>
          val json = s.parseJson.asJsObject
          val (process, message) = validateJson(json)
          if (!process) {
            log.warning(message.toString)
            orgSender ! ParserResponse(id, NoUpdate)
          } else {
            printTickets(message.fields("tickets"))
            log.info(s"processed tickets successfully")
            val state = updateState(message)
            orgSender ! ParserResponse(id, state)
          }
        case Failure(ex) =>
          log.error(ex.toString)
          orgSender ! ParserResponse(id, NoUpdate)
      }
      res.discardBytes()

    case KillParser => self ! PoisonPill
  }

  def validateJson(json: JsObject): (Boolean, JsObject) = json match {
    case json if json.fields.contains("tickets") => (true, json)
    case json if json.fields.contains("error") => (false, json)
    case _ => (false, json)
  }

  def updateState(json: JsObject): State = {
    UpdateState(
      endOfStream = json.fields("end_of_stream").convertTo[Boolean],
      nextPage = json.fields("next_page").convertTo[String].takeWhile(_ != '?'),
      endTime = json.fields("end_time").convertTo[Long]
    )
  }

  def printTickets(json: JsValue): Unit = {
    for (j <- json.convertTo[Array[JsObject]]){
      println(
        s"""
           |Created At: ${j.fields("created_at")},
           |Id: ${j.fields("id")},
           |Url: ${j.fields("url")}
           |Updated At: ${j.fields("updated_at")}
           |""".stripMargin)
    }
  }

}

object JsonParserActor {
  def props(id: String)(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext): Props = Props(new JsonParserActor(id))

  case class Payload(payload: ResponseEntity)
  case class ParserResponse(id: String, state: State)
  case object KillParser

}
