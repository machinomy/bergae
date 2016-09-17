package com.machinomy.bergae

import java.io.File
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import colossus._
import core._
import service._
import protocols.http._
import UrlParsing._
import HttpMethod._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._
import io.circe.jawn.decode
import Storage.{AddPerson, PersonParameters}
import io.circe.generic.JsonCodec

class APIService(context: ServerContext) extends HttpService(context) {

  case class GettingParameters(uuid: String)

  def handle = {
    case request @ Get on Root =>
      println(request)
      Callback.successful(request.ok("Hello world"))

    case request @ Post on Root / "persons" / "search" =>
      val paramsXor: cats.data.Xor[io.circe.Error, PersonParameters] = decode[PersonParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(parameters) =>
          val uuid = Main.storage.search(parameters)
          Callback.successful(request.ok(s"""{"uuid": $uuid"""))
      }

    case request @ Post on Root / "persons" / "new" =>
      val paramsXor: cats.data.Xor[io.circe.Error, PersonParameters] = decode[PersonParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          Main.storage.append(UUID.randomUUID(), AddPerson(params.firstName, params.lastName, params.birthDate, params.passportHash))
          Callback.successful(request.ok("""{"status":"ok"}"""))
      }

    case request @ Post on Root / "persons" / "get" =>
      val paramsXor: cats.data.Xor[io.circe.Error, GettingParameters] = decode[GettingParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val operations = Main.storage.get(UUID.fromString(params.uuid))
          val personOperationOption = operations.find(x => x.isInstanceOf[AddPerson])
          personOperationOption match {
            case Some(person) => Callback.successful(request.ok(person.asJson.noSpaces))
            case None => Callback.successful(request.notFound(s"""${params.uuid} not found"""))
          }
      }
  }
}

class APIInitializer(worker: WorkerRef) extends Initializer(worker) {
  def onConnect = context => new APIService(context)
}

object Main extends App {
  case class Arguments(config: File = new File("./application.json"))

  implicit val system = ActorSystem("bergae")
//  var configuration: Configuration = _
//  var storage: Storage = _

  var node: ActorRef = _

//  val parsedArgs = parse(args)
//  parsedArgs foreach { arguments =>
//    val props = Node.props(configuration, storage)
//    configuration = Configuration load arguments.config
//    storage = new Storage(configuration)
//    node = system actorOf props
//  }

  val configuration = parse(args) match {
    case Some(arguments) =>
      Configuration load arguments.config
    case None =>
      Configuration.load()
  }

  val storage = new Storage(configuration)
  val props = Node.props(configuration, storage)
  node = system actorOf props


  implicit val io = colossus.IOSystem()
  Server.start("api", 9000) {
    worker => new APIInitializer(worker)
  }

  def parse(args: Array[String]): Option[Arguments] = {
    val parser = new scopt.OptionParser[Arguments]("bergae") {
      head("bergae - blockless blockchain")

      opt[File]('c', "config")
        .required()
        .valueName("<file>")
        .action((f, c) => c.copy(config = f))
        .text("config is a required property")
    }
    parser.parse(args, Arguments())
  }
}
