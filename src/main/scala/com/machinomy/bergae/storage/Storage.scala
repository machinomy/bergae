package com.machinomy.bergae.storage

import java.util.UUID

import com.machinomy.bergae.crypto.{ECPub, Sha256Hash}
import io.circe.generic.JsonCodec

import scala.concurrent.Future

trait Storage {

  def append(uuid: UUID, operation: Storage.Operation): Unit

  def get(uuid: UUID): Future[Seq[Storage.Operation]]

  def height: Long

  def height_=(value: Long): Unit

  def accepted(txid: Sha256Hash): Set[ECPub]

  def accept(txid: Sha256Hash, pub: ECPub): Unit

  def mapOperation(operationHash: Sha256Hash, txid: Sha256Hash): Unit

  def mapOperation(operation: Storage.Operation, txid: Sha256Hash): Unit

  def approvals(operation: Storage.Operation): Int
}

object Storage {
  @JsonCodec
  sealed trait Operation

  object Operation
}
