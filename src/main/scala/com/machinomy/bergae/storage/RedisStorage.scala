package com.machinomy.bergae.storage

import java.util.UUID

import com.machinomy.bergae.crypto.{Digest, ECPub, Hex, Sha256Hash}
import akka.actor.ActorSystem
import akka.util.ByteString
import com.machinomy.bergae.configuration.RedisStorageConfiguration
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._
import redis.RedisClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

class RedisStorage[T <: Storage.Operation](configuration: RedisStorageConfiguration)(implicit actorSystem: ActorSystem, implicit val serializer: Storage.Serializable) extends Storage[T] {
  import actorSystem._

  //val client = new RedisClient(configuration.redis.host, configuration.redis.port)
  val client = RedisClient(configuration.host, configuration.port)
  val timeout = 5.seconds

  def append(uuid: UUID, operation: T): Unit = {
    val future: Future[Boolean] =
      operation match {
        //        case person: AddPerson =>
        //          val field: String = SearchParameters(person.firstName, person.lastName, person.passportHash).asJson.noSpaces
        //          for {
        //            _ <- client.hset("index", field, ByteString.fromString(uuid.toString))
        //          } yield {
        //            append(uuid, operation.asJson.noSpaces)
        //            true
        //          }
        case _ =>
          append(uuid, serializer.serialize[T](operation))
          Future.successful(true)
      }
    Await.ready(future, timeout)
  }

  def append(uuid: UUID, operation: String): Unit = {
    val r: Future[Long] = client.rpush(storageKey(uuid), operation)
    Await.ready(r, timeout)
  }

  def get(uuid: UUID): Future[Seq[T]] = {
    for {
      lrange <- client.lrange(storageKey(uuid), 0, -1)
    } yield {
      lrange.flatMap { byteString =>
        Option(serializer.deserialize[T](byteString.utf8String))
      }
    }
  }

  //  def search(params: SearchParameters): Option[UUID] = {
  //    val futureOpt =
  //      for {
  //        cursor <- client.hscan("index", 0, Some(10000000), Some(params.asJson.noSpaces))
  //      } yield {
  //        cursor.data.values.lastOption.map(x => UUID.fromString(x.utf8String))
  //      }
  //    Await.result(futureOpt, timeout)
  //  }

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

  private def acceptKey(hash: Sha256Hash): String = {
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

  def mapOperation(operation: String, txid: Sha256Hash): Unit = {
    val operationHash = Digest[Sha256Hash](operation)
    mapOperation(operationHash, txid)
  }

  def approvals(operation: T): Int = {
    val operationJson = serializer.serialize[T](operation)
    val operationHash = Digest[Sha256Hash](operationJson)
    approvals(operationHash)
  }

  private def approvals(operationHash: Sha256Hash): Int = {
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
