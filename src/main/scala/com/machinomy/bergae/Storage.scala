package com.machinomy.bergae

import java.awt.print.Book
import java.util.UUID

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.util.ByteString
import com.machinomy.bergae.crypto.{Digest, ECPub, Hex, Sha256Hash}
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

  def addWaiting(hash: Sha256Hash): Unit = {
    val bytes = hash.toByteArray
    val hexEncoded: String = Hex.encode(bytes)
    val a = client.sadd("waiting", hexEncoded)
    Await.result(a, timeout)
  }

  def addWaiting(hashes: Seq[Sha256Hash]): Unit = {
    for (h <- hashes) addWaiting(h)
  }

  def resetWaiting(): Unit = {
    val future =
      for {
        members <- client.smembers("waiting")
        a <- client.srem("waiting", members.map(_.utf8String))
      } yield a
    Await.ready(future, timeout)
  }

  def waiting(): Set[Sha256Hash] = {
    val future =
      for {
        members <- client.smembers("waiting")
      } yield {
        members.map { byteString =>
          val string = byteString.utf8String
          val bytes = Hex.decode(string)
          Sha256Hash(bytes.toArray)
        }
      }
    Await.result(future, timeout).toSet
  }

  def accepted(txid: Sha256Hash): Set[ECPub] = {
    val key = acceptKey(txid)
    val future =
      for {
        members <- client.smembers(key)
      } yield {
        members.map { byteString =>
          val string = byteString.utf8String
          val bytes = Hex.decode(string)
          ECPub.apply(bytes)
        }
      }
    Await.result(future, timeout).toSet
  }

  def accept(txid: Sha256Hash, pub: ECPub): Unit = {
    val key = acceptKey(txid)
    val bytes = pub.toByteArray
    val hexEncoded: String = Hex.encode(bytes)
    println(s"ACCEPT: $key -> $hexEncoded")
    val a = client.sadd(key, hexEncoded)
    Await.ready(a, timeout)
  }

  def acceptKey(hash: Sha256Hash): String = {
    val hashString = Hex.encode(hash.bytes)
    s"accepted:$hashString"
  }

  def mapOperation(operationHash: Sha256Hash, txid: Sha256Hash): Unit = {
    val hex = Hex.encode(operationHash.bytes)
    val key = s"mapOperation:$hex"
    val txidString = Hex.encode(txid.bytes)
    val f = client.set(key, txidString)
    Await.ready(f, timeout)
  }

  def mapOperation(operation: Operation, txid: Sha256Hash): Unit = {
    val operationJson = operation.asJson.noSpaces
    val operationHash = Digest[Sha256Hash](operationJson)
    mapOperation(operationHash, txid)
  }

  def approvals(operation: Operation): Int = {
    val operationJson = operation.asJson.noSpaces
    val operationHash = Digest[Sha256Hash](operationJson)
    approvals(operationHash)
  }

  def approvals(operationHash: Sha256Hash): Int = {
    val hex = Hex.encode(operationHash.bytes)
    val key = s"mapOperation:$hex"
    val future =
      for {
        txidOpt <- client.get(key)
      } yield {
        txidOpt match {
          case Some(txidBS) =>
            val txid = txidBS.utf8String
            val unhex: Seq[Byte] = Hex.decode(txid)
            val sha256Hash = Sha256Hash(unhex.toArray)
            accepted(sha256Hash).size
          case None => 0
        }
      }
    Await.result(future, timeout)
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
