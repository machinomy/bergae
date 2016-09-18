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
import com.machinomy.bergae.Storage._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object Answers {
  @JsonCodec
  sealed trait Answer
  case class CreditHistory(uuid: UUID, credits: Array[Credit]) extends Answer
  case class Credit(amount: Double, percentage: Double, time: String, date: String, uuid: String, payments: Array[Payment], status: String, closeDate: Option[String] = None) extends Answer
  case class Payment(amount: Double, date: String, creditUUID: String) extends Answer

  object Answer
}

class APIService(context: ServerContext) extends HttpService(context) {

  case class GettingParameters(uuid: String)

  case class CreditParameters(amount: Double, percentage: Double, time: String, date: String)
  case class NewCreditParameters(uuid: String, credit: CreditParameters)
  case class GettingCreditParameters(uuid: String)
  case class CloseCreditParameters(uuid: String, closeCredit: CloseCredit)
  case class NewPaymentsParameters(uuid: String, payment: AddPayment)

  def handle = {
    case request @ Get on Root =>
      println(request)
      Callback.successful(request.ok("Hello world"))

    case request @ Post on Root / "persons" / "search" =>
      val paramsXor: cats.data.Xor[io.circe.Error, SearchParameters] = decode[SearchParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(parameters) =>
          val uuidOption = Main.storage.search(parameters)
          uuidOption match {
            case Some(uuid) => Callback.successful(request.ok(s"""{"uuid": $uuid"""))
            case None => Callback.successful(request.notFound("Fuck you, pidor"))
          }

      }

    case request @ Post on Root / "persons" / "new" =>
      val paramsXor: cats.data.Xor[io.circe.Error, PersonParameters] = decode[PersonParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val newUUID = UUID.randomUUID()
          Main.storage.append(newUUID, AddPerson(params.firstName, params.lastName, params.birthDate, params.passportHash))
          Callback.successful(request.ok(s"""{"uuid":"$newUUID"}"""))
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

    case request @ Post on Root / "credits" / "new" =>
      val paramsXor: cats.data.Xor[Error, NewCreditParameters] = decode[NewCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val newUUID = UUID.randomUUID()
          val addCreditOperation = AddCredit(params.credit.amount, params.credit.percentage, params.credit.time, params.credit.date, newUUID.toString)
          Main.storage.append(UUID.fromString(params.uuid), addCreditOperation)
          Callback.successful(request.ok(s"""{"creditUUID":"$newUUID"}"""))
      }

    case request @ Post on Root / "credits" / "get" =>
      val paramsXor: cats.data.Xor[Error, GettingCreditParameters] = decode[GettingCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val answer = Answers.CreditHistory(UUID.fromString(params.uuid), Array.empty[Answers.Credit])
          val operations = Main.storage.get(UUID.fromString(params.uuid))

          def generateStructure(operations: Seq[Operation], accumulator: Answers.CreditHistory): Answers.CreditHistory = {
            operations.headOption match {
              case Some(op) =>
                op match {
                  case addCredit: AddCredit =>
                    val credit = Answers.Credit(addCredit.amount, addCredit.percentage, addCredit.time, addCredit.date, addCredit.uuid, Array.empty[Answers.Payment], "opened")
                    val nextCredits = accumulator.credits :+ credit
                    val next = accumulator.copy(credits = nextCredits)
                    generateStructure(operations.tail, next)
                  case addPayment: AddPayment =>
                    val payment = Answers.Payment(addPayment.amount, addPayment.date, addPayment.creditUUID.toString)
                    val nextCredits = accumulator.credits.map {
                      case credit if credit.uuid == payment.creditUUID =>
                        credit.copy(payments = credit.payments :+ payment)
                      case credit => credit
                    }
                    val next = accumulator.copy(credits = nextCredits)
                    generateStructure(operations.tail, next)
                  case closeCredit: CloseCredit =>
                    val nextCredits = accumulator.credits.map {
                      case credit if credit.uuid == closeCredit.creditUUID =>
                        credit.copy(status = "closed", closeDate = Some(closeCredit.date))
                      case credit => credit
                    }
                    val next = accumulator.copy(credits = nextCredits)
                    generateStructure(operations.tail, next)
                  case _ => generateStructure(operations.tail, accumulator)
                }
              case None => accumulator
            }
          }

          Callback.successful(request.ok(generateStructure(operations, answer).asJson.noSpaces))
      }

    case request @ Post on Root / "credits" / "yabyrga" =>
      val paramsXor: cats.data.Xor[Error, CloseCreditParameters] = decode[CloseCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          Main.storage.append(UUID.fromString(params.uuid), params.closeCredit)
          Callback.successful(request.ok("""{"ok":"status"}"""))
      }

    case request @ Post on Root / "credits" / "payments" / "new" =>
      val paramsXor: cats.data.Xor[Error, NewPaymentsParameters] = decode[NewPaymentsParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          Main.storage.append(UUID.fromString(params.uuid), params.payment)
          Callback.successful(request.ok("""{"vsyo": ["ochen", "horosho"]}"""))
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
