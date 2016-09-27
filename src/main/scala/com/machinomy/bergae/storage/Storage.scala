package com.machinomy.bergae.storage

import java.util.UUID

import com.machinomy.bergae.crypto.{ECPub, Sha256Hash}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._
import scala.concurrent.Future

trait Storage[T <: Storage.Operation] {

  def append(uuid: UUID, operation: T): Unit

  def append(uuid: UUID, operation: String): Unit

  def get(uuid: UUID): Future[Seq[T]]

  def height: Long

  def height_=(value: Long): Unit

  def accepted(txid: Sha256Hash): Set[ECPub]

  def accept(txid: Sha256Hash, pub: ECPub): Unit

  def mapOperation(operationHash: Sha256Hash, txid: Sha256Hash): Unit

  def mapOperation(operation: String, txid: Sha256Hash): Unit

  def approvals(operation: T): Int
}

object Storage {
  trait Operation

  trait Serializable[T] {
    def serialize(operation: T): String
    def deserialize(str: String): T
  }

}
