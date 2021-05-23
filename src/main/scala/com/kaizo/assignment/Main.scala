package com.kaizo.assignment

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext.Implicits.global
import com.kaizo.assignment.actors.SupervisorActor
import com.kaizo.assignment.actors.SupervisorActor.CreateCustomerStream
import com.kaizo.assignment.server._
object Main {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {

    val superVisor = system.actorOf(SupervisorActor.props, "SuperVisor")
    val api = new Api(superVisor)
    Http().bindAndHandle(api.startPath, "localhost", 8080)

  }

}
