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
import com.machinomy.bergae.crypto.Hex
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

object Answers {
  @JsonCodec
  sealed trait Answer
  case class CreditHistory(uuid: UUID, credits: Array[Credit]) extends Answer
  case class Credit(amount: Double, percentage: Double, time: String, date: String, uuid: String, payments: Array[Payment], status: String, approved: Boolean = false, closeDate: Option[String] = None) extends Answer
  case class Payment(amount: Double, date: String, creditUUID: String, approved: Boolean = false) extends Answer

  object Answer
}

class APIService(context: ServerContext, node: ActorRef, storage: Storage, configuration: Configuration)(implicit ec: ExecutionContext) extends HttpService(context) {

  case class GettingParameters(uuid: String)

  case class CreditParameters(amount: Double, percentage: Double, time: String, date: String)
  case class NewCreditParameters(uuid: String, credit: CreditParameters)
  case class GettingCreditParameters(uuid: String)
  case class CloseCreditParameters(uuid: String, closeCredit: CloseCredit)
  case class NewPaymentsParameters(uuid: String, payment: AddPayment)

  implicit object TwirlEncoder extends HttpBodyEncoder[play.twirl.api.Html] {
    val ctype = HttpHeader("Content-Type", "text/html")
    def encode(data: play.twirl.api.Html) : HttpBody = new HttpBody(data.toString.getBytes("UTF-8"), Some(ctype))
  }

  case class JSON(s: String)

  implicit object JSONEncoder extends HttpBodyEncoder[JSON] {
    val ctype = HttpHeader("Content-Type", "application/json")
    def encode(data: JSON) : HttpBody = new HttpBody(data.s.getBytes("UTF-8"), Some(ctype))
  }

  def handle: PartialFunction[HttpRequest, Callback[HttpResponse]] = {
    case request @ Get on Root =>
      println(request)
      Callback.successful(request.ok("Hello world"))

    case request @ Get on Root / "graph.json" =>
      var allTxids: Set[String] = Set.empty
      val elements = storage.allApproveLinks().flatMap { txid =>
        val hex = Hex.encode(txid.toByteArray)
        allTxids += hex
        storage.getApproveLinks(txid).map { linkTxid =>
          val linkedTxid = Hex.encode(linkTxid.toByteArray)
          allTxids += linkedTxid
          s"""{"source": "$hex", "target": "$linkedTxid"}"""
        }
      }
      val nodes = allTxids.map { txid => s"""{"id": "$txid"}"""}
      val result = JSON(s"""{"links": [${elements.mkString(", ")}], "nodes": [${nodes.mkString(",")}]}""")
      Callback.successful(request.ok(result))

    case request @ Get on Root / "graph" =>
      val templateString: play.twirl.api.Html = html.graph()
      Callback.successful(request.ok(templateString))

    case request @ Post on Root / "persons" / "search" =>
      val paramsXor: cats.data.Xor[io.circe.Error, SearchParameters] = decode[SearchParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(parameters) =>
          val uuidOption = Main.storage.search(parameters)
          uuidOption match {
            case Some(uuid) => Callback.successful(request.ok(s"""{"uuid": "$uuid"}"""))
            case None => Callback.successful(request.notFound("Fuck you, pidor"))
          }

      }

    case request @ Post on Root / "persons" / "new" =>
      val paramsXor: cats.data.Xor[io.circe.Error, PersonParameters] = decode[PersonParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val newUUID = UUID.randomUUID()
          val operation = AddPerson(params.firstName, params.lastName, params.birthDate, params.passportHash)
          node ! Node.Update(newUUID, operation)
          Callback.successful(request.ok(s"""{"uuid":"$newUUID"}"""))
      }

    case request @ Post on Root / "persons" / "get" =>
      val paramsXor: cats.data.Xor[io.circe.Error, GettingParameters] = decode[GettingParameters](request.body.toString())
      val future =
        paramsXor match {
          case cats.data.Xor.Left(failure) =>
            Future.successful(request.badRequest(failure.toString))
          case cats.data.Xor.Right(params) =>
            for {
              operations <- Main.storage.get(UUID.fromString(params.uuid))
            } yield {
              val personOperationOption = operations.find(x => x.isInstanceOf[AddPerson])
              personOperationOption match {
                case Some(person) => request.ok(person.asJson.noSpaces)
                case None => request.notFound(s"""${params.uuid} not found""")
              }
            }
        }
      Callback.fromFuture(future)

    case request @ Post on Root / "credits" / "new" =>
      val paramsXor: cats.data.Xor[Error, NewCreditParameters] = decode[NewCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val newUUID = UUID.randomUUID()
          val addCreditOperation = AddCredit(params.credit.amount, params.credit.percentage, params.credit.time, params.credit.date, newUUID.toString)
          val creditUuid = UUID.fromString(params.uuid)
          node ! Node.Update(creditUuid, addCreditOperation)
          Callback.successful(request.ok(s"""{"creditUUID":"$newUUID"}"""))
      }

    case request @ Post on Root / "credits" / "get" =>
      val paramsXor: cats.data.Xor[Error, GettingCreditParameters] = decode[GettingCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val answer = Answers.CreditHistory(UUID.fromString(params.uuid), Array.empty[Answers.Credit])
          val future =
            for {
              operations <- Main.storage.get(UUID.fromString(params.uuid))
            } yield {
              request.ok(generateStructure(operations, answer).asJson.noSpaces)
            }
          Callback.fromFuture(future)
      }

    case request @ Post on Root / "credits" / "yabyrga" =>
      val paramsXor: cats.data.Xor[Error, CloseCreditParameters] = decode[CloseCreditParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val operation = params.closeCredit
          val uuid = UUID.fromString(params.uuid)
          node ! Node.Update(uuid, operation)
          Callback.successful(request.ok("""{"ok":"status"}"""))
      }

    case request @ Post on Root / "credits" / "payments" / "new" =>
      val paramsXor: cats.data.Xor[Error, NewPaymentsParameters] = decode[NewPaymentsParameters](request.body.toString())
      paramsXor match {
        case cats.data.Xor.Left(failure) => Callback.successful(request.badRequest(failure.toString))
        case cats.data.Xor.Right(params) =>
          val operation = params.payment
          val uuid = UUID.fromString(params.uuid)
          node ! Node.Update(uuid, operation)
          Callback.successful(request.ok("""{"vsyo": ["ochen", "horosho"]}"""))
      }
  }

  def isApproved(operation: Operation): Boolean = {
    val approvals = storage.approvals(operation)
    val keys = configuration.seeds.size
    approvals == keys
  }

  def generateStructure(operations: Seq[Operation], accumulator: Answers.CreditHistory): Answers.CreditHistory = {
    operations.headOption match {
      case Some(op) =>
        op match {
          case addCredit: AddCredit =>
            val credit = Answers.Credit(addCredit.amount, addCredit.percentage, addCredit.time, addCredit.date, addCredit.uuid, Array.empty[Answers.Payment], "opened", isApproved(addCredit))
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
}

class APIInitializer(worker: WorkerRef, node: ActorRef, storage: Storage, configuration: Configuration)(implicit ec: ExecutionContext) extends Initializer(worker) {
  def onConnect = context => new APIService(context, node, storage, configuration)
}

object Main extends App {
  case class Arguments(config: File = new File("./application.json"))

  implicit val system = ActorSystem("bergae")

  var node: ActorRef = _

  val configuration = parse(args) match {
    case Some(arguments) =>
      Configuration.load(arguments.config)
    case None =>
      Configuration.load()
  }

  val storage = new Storage(configuration)
  val props = Node.props(configuration, storage)
  node = system actorOf props

  configuration.httpOpt.foreach { httpConfiguration =>
    implicit val io = colossus.IOSystem()
    Server.start(httpConfiguration.name, httpConfiguration.port) {
      worker => new APIInitializer(worker, node, storage, configuration)(system.dispatcher)
    }
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
