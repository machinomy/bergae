package com.machinomy.bergae

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import cats.data.Xor
import com.machinomy.xicity.mac.{Message, Parameters}
import com.machinomy.xicity.network.{FullNode, Peer, PeerBase}
import com.github.nscala_time.time.Imports._
import com.machinomy.bergae.Messaging.Signed
import com.machinomy.bergae.crypto.{ECPub, Sha256Hash}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

import scala.concurrent.duration._
import scala.util.Random

class Node(configuration: Configuration) extends Actor with ActorLogging {
  import context._

  val expirationTimeout = 2000

  var upstream: ActorRef = _

  var afterReceiveTicker: Option[Cancellable] = None
  var afterSendTicker: Option[Cancellable] = None

  var accepted: Map[Sha256Hash, Set[ECPub]] = Map.empty[Sha256Hash, Set[ECPub]]
  var waiting: Set[Sha256Hash] = Set.empty[Sha256Hash]
  var transactions: Map[Sha256Hash, Signed] = Map.empty[Sha256Hash, Signed]

  var storage = new Storage(configuration)

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
    case Node.Update(uuid, string) =>
      log.info(s"Sending UPDATE")
      height = height + 1
      val message = Messaging.Update(height, uuid, string, waiting)
      waiting = Set.empty[Sha256Hash]
      append(uuid, string)
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
              for (txid <- approve) accept(txid, signed.pub)
              waiting = waiting + signed.txid
              log.info(s"Reached someone")
              if (i >= height) {
                height = i + 1
                log.info(s"Setting height to $height, as $i was received")
              } else {
                log.info(s"Sending height $height as a response to Nop")
                broadcast(Messaging.Nop(height, Set(signed.txid)))
              }
            case msg @ Messaging.Update(time, uuid, string, approve) =>
              if (time >= height) {
                height = time + 1
              }
              accept(signed.txid, signed.pub)
              accept(signed.txid, configuration.me.pub) // @fixme
              for (txid <- approve) accept(txid, signed.pub)
              waiting = waiting + signed.txid
              log.info(s"Got update: $uuid: $string")
              append(uuid, string)
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
    val signed = Messaging.signed(payload, configuration.key)
    accept(signed.txid, configuration.me.pub)
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
    val nextAccepted: Set[ECPub] = accepted.getOrElse(txid, Set.empty[ECPub]) + pub
    accepted = accepted.updated(txid, nextAccepted)
  }

  def append(uuid: UUID, string: String): Unit = {
    storage.append(uuid, string)
  }

  def height: Long = storage.height

  def height_=(value: Long) = storage.height = value
}

object Node {
  def props(configuration: Configuration) = Props(classOf[Node], configuration)

  sealed trait Msg
  case object Nop extends Msg
  case class Update(uuid: UUID, string: String) extends Msg
}
