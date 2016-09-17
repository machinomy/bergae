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

  /*def append(uuid: UUID, person: AddPerson): Unit = {
    val operation: Operation = person
    val string = operation.asJson.noSpaces
    append(uuid, string)
  }

  def append(uuid: UUID, addCredit: AddCredit): Unit = {
    val operation: Operation = addCredit
    val string = operation.asJson.noSpaces
    append(uuid, string)
  }

  def append(uuid: UUID, addPayment: AddPayment): Unit = {
    val operation: Operation = addPayment
    val string = operation.asJson.noSpaces
    append(uuid, string)
  }

  def append(uuid: UUID, closeCredit: CloseCredit): Unit = {
    val operation: Operation = closeCredit
    val string = operation.asJson.noSpaces
    append(uuid, string)
  }

  def append(uuid: UUID, makeCheck: MakeCheck): Unit = {
    val operation: Operation = makeCheck
    val string = operation.asJson.noSpaces
    append(uuid, string)
  }*/

  def append(uuid: UUID, operation: Operation): Unit = {
    append(uuid, operation.asJson.noSpaces)
  }

  def append(uuid: UUID, string: String): Unit = {
    client.rpush(uuid, string)
  }

  def get(uuid: UUID): Seq[Opearation] = {
    Seq.empty
  }

  def search(params: PersonParameters): UUID = {
    UUID.randomUUID
  }

  def height: Long = client.get("height").map(_.toLong).getOrElse(0)

  def height_=(value: Long) = client.set("height", value)
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
