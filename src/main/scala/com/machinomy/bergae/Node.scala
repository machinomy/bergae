package com.machinomy.bergae

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import cats.data.Xor
import com.machinomy.xicity.mac.{Message, Parameters}
import com.machinomy.xicity.network.{FullNode, Peer, PeerBase}
import com.github.nscala_time.time.Imports._
import com.machinomy.bergae.Messaging.{Payload, Signed}
import com.machinomy.bergae.crypto.{ECPub, Sha256Hash}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

import scala.concurrent.duration._

class Node(configuration: Configuration, storage: Storage) extends Actor with ActorLogging {
  import context._

  val expirationTimeout = 2000

  var upstream: ActorRef = _

  var afterReceiveTicker: Option[Cancellable] = None
  var afterSendTicker: Option[Cancellable] = None

  var waiting: Set[Sha256Hash] = Set.empty[Sha256Hash]

  val crypto = new Crypto(configuration)

  override def preStart(): Unit = {
    val parameters = Parameters.default
    val upstreamProps = PeerBase.props[FullNode](configuration.me.xicity, self, parameters)
    upstream = context.actorOf(upstreamProps, "upstream")
  }

  override def receive: Receive = {
    case Peer.IsReady() =>
      log.info(s"Ready")
      resetAfterReceiveTicker()
    case Node.Nop =>
      log.info(s"Sending Nop...")
      val message = Messaging.Nop(height, waiting)
      waiting = Set.empty[Sha256Hash]
      broadcast(message)
    case Node.Update(uuid, operation) =>
      log.info(s"Sending UPDATE")
      height = height + 1
      val message = Messaging.Update(height, uuid, operation, waiting)
      waiting = Set.empty[Sha256Hash]
      append(uuid, operation)
      broadcast(message)
    case Peer.Received(message: Message.Single) =>
      log.info(s"RECEIVED SINGLE: $message")
      val jsonString = new String(message.text)
      parser.decode[Signed](jsonString) match {
        case Xor.Left(error) =>
          log.error(s"RECEIVED ERROR: $error")
        case Xor.Right(signed) =>
          resetAfterReceiveTicker()
          println(s"DECODED: ${signed.payload}")
          signed.payload match {
            case msg @ Messaging.Nop(i, approve) =>
              accept(signed.txid, signed.pub)
              accept(signed.txid, configuration.me.pub)
              for (txid <- approve) {
                if (storage.accepted(txid).contains(configuration.me.pub) && storage.accepted(txid).size == 1) {
                  waiting = waiting + txid
                }
                accept(txid, signed.pub)
                storage.approveLink(signed.txid, txid)
              }
              waiting = waiting + signed.txid
              log.info(s"Reached someone")
              if (i >= height) {
                height = i + 1
                log.info(s"Setting height to $height, as $i was received")
              } else {
                log.info(s"Sending height $height as a response to Nop")
                broadcast(Messaging.Nop(height, waiting + signed.txid))
                waiting = Set.empty[Sha256Hash]
              }
            case msg @ Messaging.Update(time, uuid, operation, approve) =>
              if (time >= height) {
                height = time + 1
              }
              accept(signed.txid, signed.pub)
              accept(signed.txid, configuration.me.pub)
              mapOperation(operation, signed.txid)
              for (txid <- approve) {
                if (storage.accepted(txid).contains(configuration.me.pub) && storage.accepted(txid).size == 1) {
                  waiting = waiting + txid
                }
                accept(txid, signed.pub)
                storage.approveLink(signed.txid, txid)
              }
              waiting = waiting + signed.txid
              log.info(s"Got update: $uuid: $operation")
              append(uuid, operation)
          }
      }
    case something =>
      log.info(s"RECEIVED $something")
  }

  def resetAfterSendTicker(): Unit = {
    if (afterSendTicker.isDefined) {
      afterSendTicker.foreach(_.cancel())
    }
    val randomTimeout = 6000.milliseconds
    log.info(s"Set after send ticker to $randomTimeout")
    afterSendTicker = Some(context.system.scheduler.scheduleOnce(randomTimeout)(self ! Node.Nop))
  }

  def resetAfterReceiveTicker(): Unit = {
    if (afterReceiveTicker.isDefined) {
      afterReceiveTicker.foreach(_.cancel())
    }
    val randomTimeout = 2000.milliseconds
    log.info(s"Set after receive ticker to $randomTimeout")
    afterReceiveTicker = Some(context.system.scheduler.scheduleOnce(randomTimeout)(self ! Node.Nop))
  }

  def broadcast(payload: String): Unit = broadcast(payload.getBytes)

  def broadcast(payload: Messaging.Payload): Unit = {
    val signed = Messaging.signed(crypto, payload, configuration.key)
    accept(signed.txid, configuration.me.pub)
    payload match {
      case m: Messaging.Update =>
        mapOperation(m.operation, signed.txid)
        for (txid <- m.approve) {
          storage.approveLink(signed.txid, txid)
        }
        log.info(s"PUBLISHING ${m.operation}")
      case _ =>
    }
    broadcast(signed.jsonString)
    resetAfterSendTicker()
    if (afterReceiveTicker.isDefined) {
      afterReceiveTicker.foreach(_.cancel())
    }
  }

  def broadcast(payload: Array[Byte]): Unit = {
    for {
      other <- configuration.seeds
      if other.xicity != configuration.me.xicity
    } {
      val expiration = DateTime.now + expirationTimeout
      val message = Message.Single(configuration.me.xicity, other.xicity, payload, expiration)
      upstream ! message
    }
  }

  def accept(txid: Sha256Hash, pub: ECPub): Unit = {
    storage.accept(txid, pub)
  }

  def mapOperation(operation: Storage.Operation, txid: Sha256Hash): Unit = {
    storage.mapOperation(operation, txid)
  }

  def mapOperation(operationId: Sha256Hash, txid: Sha256Hash): Unit = {
    storage.mapOperation(operationId, txid)
  }

  def append(uuid: UUID, string: String): Unit = {
    storage.append(uuid, string)
  }

  def append(uuid: UUID, operation: Storage.Operation): Unit = {
    storage.append(uuid, operation)
  }

  def ifVerified(signed: Signed)(handle: Signed => Unit): Unit = {
    val payloadBytes = signed.payload.asJson.noSpaces.getBytes
    val verified = crypto.verify(payloadBytes, signed.signature, signed.pub)
    if (verified) {
      handle(signed)
    } else {
      log.error(s"Can not verify $signed")
    }
  }

  def height: Long = storage.height

  def height_=(value: Long) = storage.height = value
}

object Node {
  def props(configuration: Configuration, storage: Storage) = Props(classOf[Node], configuration, storage)

  sealed trait Msg
  case object Nop extends Msg
  case class Update(uuid: UUID, operation: Storage.Operation) extends Msg
}
