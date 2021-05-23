package com.kaizo.assignment.actors

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.stream.ActorMaterializer
import com.kaizo.assignment.actors.JsonParserActor.{KillParser, ParserResponse, Payload}
import spray.json.JsonFormat
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext

class RestClientActor(clientId: String, token: String)(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext)
  extends PersistentActor with ActorLogging {

  import RestClientActor._

  private var parsers = Map.empty[String, ActorRef]

  private var state = StreamState()

  private val schedule = context.system.scheduler.schedule(6 seconds, 6 seconds){
    self ! PingEndpoint
  }

  override val persistenceId: String = clientId

  override def receiveCommand: Receive = {
    case PingEndpoint => {
      val request = Http().singleRequest(
        HttpRequest(uri = Uri(state.nextPage)
          .withQuery(Query("start_time" -> state.endTime.toString))
        ).addCredentials(OAuth2BearerToken(token)))
      request
        .map(res => Payload(res.entity))
        .pipeTo(getParser(java.util.UUID.randomUUID.toString))
    }
    case ParserResponse(id, UpdateState(endOfStream, nextPage, endTime)) =>
      if (state.endTime < endTime){
        persist(StreamState(endOfStream, nextPage, endTime)) { event =>
          state = StreamState(endOfStream, nextPage, endTime)
          stopParser(id)
          log.info(s"Persisted state up to: $endTime")
        }
      } else {
        log.info(s"Pinged Endpoint: Stream up to date")
      }
    case ParserResponse(id, NoUpdate) =>
      stopParser(id)
    case AskState => sender() ! state
    case StopStream =>
      schedule.cancel()
      self ! PoisonPill
      log.info(s"Stopped Stream $clientId")
  }

  override def receiveRecover: Receive = {
    case StreamState(endOfStream, nextPage, endTime) =>
      state = StreamState(endOfStream, nextPage, endTime)
      log.info(s"Recovered state up to: $endTime")
  }

  private def stopParser(id: String): Unit = {
    parsers(id) ! KillParser
    parsers -= id
  }

  private def getParser(id: String):ActorRef = {
    val parser = context.actorOf(JsonParserActor.props(id), id)
    parsers += (id -> parser)
    parser
  }

}

object RestClientActor {

  def props(clientId: String, token: String)(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext): Props = Props(new RestClientActor(clientId, token))

  case object PingEndpoint
  case object AskState
  case object StopStream
  implicit val streamStateFormat: JsonFormat[StreamState] = jsonFormat3(StreamState)
  trait State
  case class StreamState(
                          endOfStream: Boolean = false,
                          nextPage: String = "https://d3v-kaizo.zendesk.com/api/v2/incremental/tickets.json",
                          endTime: Long = 0L
                        ) extends State
  case class UpdateState(endOfStream: Boolean, nextPage: String, endTime: Long) extends State
  case object NoUpdate extends State

}
