package com.machinomy.bergae

import java.util.UUID

import com.redis.RedisClient
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

class Storage(configuration: Configuration) {
  import Storage._

  val client = new RedisClient(configuration.redis.host, configuration.redis.port)

  def append(uuid: UUID, operation: Operation): Unit = {
    append(uuid, operation.asJson.noSpaces)
    operation match {
      case person: AddPerson =>
        client.hset("index", SearchParameters(person.firstName, person.lastName, person.passportHash).asJson.noSpaces, uuid)
      case _ =>
    }
  }

  def append(uuid: UUID, string: String): Unit = {
    client.rpush(key(uuid), string)
  }

  def get(uuid: UUID): Seq[Operation] = {
    val operationStrings = client.lrange(key(uuid), 0, -1).getOrElse(Seq.empty[Option[String]]).flatten
    operationStrings.flatMap { operationString =>
      parser.decode[Operation](operationString).toOption
    }
  }

  def search(params: SearchParameters): Option[UUID] = {
    client.hscan("index", 0, params.asJson.noSpaces, 1000000).flatMap(_._2).getOrElse(List.empty[Option[String]]).flatten.lastOption.map(x => UUID.fromString(x))
  }

  def height: Long = client.get("height").map(_.toLong).getOrElse(0)

  def height_=(value: Long) = client.set("height", value)

  def key(uuid: UUID): String = s"storage:$uuid"
}

object Storage {
  @JsonCodec
  sealed trait Operation
  case class AddPerson(firstName: String, lastName: String, birthDate: String, passportHash: String) extends Operation
  case class AddCredit(amount: Double, percentage: Double, currency: String) extends Operation
  case class AddPayment(amount: Double, date: String) extends Operation
  case class CloseCredit(date: String) extends Operation
  case class MakeCheck(amount: Double, days: Int, percentage: Double, result: Boolean) extends Operation

  case class PersonParameters(firstName: String, lastName: String, birthDate: String, passportHash: String)
  case class SearchParameters(firstName: String, lastName: String, passportHash: String)

  object Operation
}
