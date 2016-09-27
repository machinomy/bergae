package com.machinomy.bergae

import java.util.UUID

import cats.data.Xor
import com.machinomy.bergae.crypto._
import com.machinomy.bergae.storage.Storage
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

import scala.util.Try

private[bergae] object Messaging {
  @JsonCodec
  sealed trait Payload
  case class Nop(height: Long, approve: Set[Sha256Hash] = Set.empty[Sha256Hash]) extends Payload
  case class Update(height: Long, uuid: UUID, operation: String, approve: Set[Sha256Hash] = Set.empty[Sha256Hash]) extends Payload

  object Payload

  case class Signed(payload: Payload, pub: ECPub, signature: ECSignature) {
    lazy val txid: Sha256Hash = Digest[Sha256Hash](jsonString)
    lazy val jsonString: String = toJsonString(this)
  }

  def signed(crypto: Crypto, payload: Payload, key: ECKey): Signed = {
    val payloadBytes = payload.asJson.noSpaces.getBytes
    val signature = crypto.sign(payloadBytes, key)
    Signed(payload, key.pub, signature)
  }

  def toJsonString(signed: Signed): String = {
    signed.asJson.noSpaces
  }

  def fromJsonString(string: String): Option[Signed] = {
    parser.decode[Signed](string).toOption
  }

  implicit val encodeUUID: Encoder[UUID] = Encoder.encodeString.contramap { uuid =>
    uuid.toString
  }

  implicit val decodeUUID: Decoder[UUID] = Decoder.decodeString.map { string =>
    UUID.fromString(string)
  }

  implicit val encodedSha256Hash: Encoder[Sha256Hash] = Encoder.encodeString.contramap { hash =>
    Hex.encode(hash.bytes)
  }

  implicit val decodeSha256Hash: Decoder[Sha256Hash] = Decoder.decodeString.map { string =>
    Sha256Hash(Hex.decode(string).toArray)
  }

  implicit val encodeSigned = new Encoder[Signed] {
    override def apply(a: Signed): Json = {
      val fields: Map[String, Json] = Map(
        "payload" -> a.payload.asJson,
        "pub" -> implicitly[Encoder[ECPub]].apply(a.pub),
        "sig" -> implicitly[Encoder[ECSignature]].apply(a.signature)
      )
      Json.fromFields(fields)
    }
  }

  implicit val decodeSigned: Decoder[Signed] = Decoder.decodeJsonObject.emapTry { jsonObject =>
    val fields = jsonObject.toMap
    val payloadTry: Try[Payload] = fields("payload").as[Payload].toTry
    val pubTry: Try[ECPub] = fields("pub").as[String].toTry.map(s => ECPub(Base58Check.decode(s)._2))
    val sigTry: Try[ECSignature] = fields("sig").as[String].toTry.map(s => ECSignature.decode(Base58Check.decode(s)._2))
    for {
      payload <- payloadTry
      pub <- pubTry
      sig <- sigTry
    } yield Signed(payload, pub, sig)
  }

  implicit val encodeECPub: Encoder[ECPub] = Encoder.encodeString.contramap { pub =>
    Base58Check.encode(Base58Check.Prefix.PublicKey, pub.toByteArray)
  }

  implicit val decodeECPub: Decoder[ECPub] = Decoder.decodeString.emap { string =>
    Xor.catchNonFatal(ECPub(Base58Check.decode(string)._2)).leftMap(_ => "ECPub")
  }

  implicit val encodeECSignature: Encoder[ECSignature] = Encoder.encodeString.contramap { signature =>
    val bytes = ECSignature.encode(signature)
    Base58Check.encode(Base58Check.Prefix.Signature, bytes)
  }

  implicit val decodeECSignature: Decoder[ECSignature] = Decoder.decodeString.emap { string =>
    Xor.catchNonFatal(ECSignature.decode(Base58Check.decode(string)._2)).leftMap(_ => "ECSignature")
  }
}
