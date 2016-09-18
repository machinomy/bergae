package com.machinomy.bergae

import java.awt.print.Book
import java.util.UUID

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.util.ByteString
import redis.{Cursor, RedisClient}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

class Storage(configuration: Configuration)(implicit actorSystem: ActorSystem) {
  import Storage._
  import actorSystem._

  //val client = new RedisClient(configuration.redis.host, configuration.redis.port)
  val client = RedisClient(configuration.redis.host, configuration.redis.port)
  val timeout = 5.seconds

  def append(uuid: UUID, operation: Operation): Unit = {
    val future: Future[Boolean] =
      operation match {
        case person: AddPerson =>
          val field: String = SearchParameters(person.firstName, person.lastName, person.passportHash).asJson.noSpaces
          for {
            _ <- client.hset("index", field, uuid.toString)
          } yield {
            append(uuid, operation.asJson.noSpaces)
            true
          }
        case _ =>
          append(uuid, operation.asJson.noSpaces)
          Future.successful(true)
      }
    Await.ready(future, timeout)
  }

  def append(uuid: UUID, string: String): Unit = {
    val r: Future[Long] = client.rpush(key(uuid), string)
    Await.ready(r, timeout)
  }

  def get(uuid: UUID): Future[Seq[Operation]] = {
    for {
      lrange <- client.lrange(key(uuid), 0, -1)
    } yield {
      lrange.flatMap { byteString =>
        parser.decode[Operation](byteString.utf8String).toOption
      }
    }
  }

  def search(params: SearchParameters): Option[UUID] = {
    val futureOpt =
      for {
        cursor <- client.hscan("index", 0, Some(10000000), Some(params.asJson.noSpaces))
      } yield {
        cursor.data.values.lastOption.map(x => UUID.fromString(x.toString))
      }
    Await.result(futureOpt, timeout)
  }

  def height: Long = {
    val futureLong: Future[Long] = client.get("height").map { optString =>
      val a: Option[Long] = Try(optString.toString.toLong).toOption
      val b: Long = a.getOrElse(0L)
      b
    }
    Await.result(futureLong, timeout)
  }

  def height_=(value: Long): Unit = {
    val future = client.set("height", value)
    Await.result(future, timeout)
  }

  def key(uuid: UUID): String = s"storage:$uuid"
}

object Storage {
  @JsonCodec
  sealed trait Operation
  case class AddPerson(firstName: String, lastName: String, birthDate: String, passportHash: String) extends Operation

  case class AddCredit(amount: Double, percentage: Double, time: String, date: String, uuid: String) extends Operation
  case class AddPayment(amount: Double, date: String, creditUUID: UUID) extends Operation
  case class CloseCredit(date: String, creditUUID: String) extends Operation

  case class MakeCheck(amount: Double, days: Int, percentage: Double, result: Boolean) extends Operation

  case class PersonParameters(firstName: String, lastName: String, birthDate: String, passportHash: String)
  case class SearchParameters(firstName: String, lastName: String, passportHash: String)

  object Operation
}
