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

  def search(params: PersonParameters): UUID = {
    UUID.randomUUID
  }

  def height: Long = client.get("height").map(_.toLong).getOrElse(0)

  def height_=(value: Long) = client.set("height", value)

  def key(uuid: UUID): String = s"storage:$uuid"
}

object Storage {
  @JsonCodec
  sealed trait Operation
  case class AddPerson(name: String, lastName: String, surname: String, passportNumber: Int, passportSeries: Int, taxId: String, birthDate: String, birthPlace: String) extends Operation
  case class AddCredit(amount: Double, percentage: Double, currency: String) extends Operation
  case class AddPayment(amount: Double, date: String) extends Operation
  case class CloseCredit(date: String) extends Operation
  case class MakeCheck(amount: Double, days: Int, percentage: Double, result: Boolean) extends Operation

  case class PersonParameters(firstName: String, lastName: String, passportNumber: Int, passportSeries: Int)

  object Operation
}
