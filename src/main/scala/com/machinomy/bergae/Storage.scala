package com.machinomy.bergae

import java.awt.print.Book
import java.util.UUID

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.util.ByteString
import com.machinomy.bergae.crypto.{ECPub, Hex, Sha256Hash}
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
            _ <- client.hset("index", field, ByteString.fromString(uuid.toString))
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
    val r: Future[Long] = client.rpush(storageKey(uuid), string)
    Await.ready(r, timeout)
  }

  def get(uuid: UUID): Future[Seq[Operation]] = {
    for {
      lrange <- client.lrange(storageKey(uuid), 0, -1)
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
        cursor.data.values.lastOption.map(x => UUID.fromString(x.utf8String))
      }
    Await.result(futureOpt, timeout)
  }

  def height: Long = {
    val futureLong: Future[Long] = client.get("height").map { (optString: Option[ByteString]) =>
      val a: Option[Long] = Try(optString.map(_.utf8String.toLong)).toOption.flatten
      val b: Long = a.getOrElse(0L)
      b
    }
    Await.result(futureLong, timeout)
  }

  def height_=(value: Long): Unit = {
    val future = client.set("height", value)
    Await.result(future, timeout)
  }

  def accepted(hash: Sha256Hash): Set[ECPub] = {
    val key = acceptKey(hash)
    val future =
      for {
        members <- client.smembers(key)
      } yield {
        members.map { byteString =>
          ECPub.apply(byteString.toArray)
        }
      }
    Await.result(future, timeout).toSet
  }

  def accept(hash: Sha256Hash, pub: ECPub): Unit = {
    val key = acceptKey(hash)
    val hexEncoded: String = Hex.encode(pub.toByteArray)
    val a = client.sadd(key, hexEncoded)
    Await.ready(a, timeout)
  }

  def acceptKey(hash: Sha256Hash): String = {
    val hashString = Hex.encode(hash.bytes)
    s"accepted:$hashString"
  }

  def storageKey(uuid: UUID): String = s"storage:$uuid"
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
