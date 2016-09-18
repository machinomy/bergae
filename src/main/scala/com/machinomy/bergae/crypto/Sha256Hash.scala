package com.machinomy.bergae.crypto

import cats.Show
import org.bouncycastle.crypto.digests.SHA256Digest

case class Sha256Hash(bytes: Array[Byte]) {
  assert(bytes.length == 32)

  def toByteArray: Array[Byte] = bytes
}

object Sha256Hash {
  implicit object Digest extends Digest[Sha256Hash] {
    val digest = new SHA256Digest()

    override def apply(message: Seq[Byte]): Sha256Hash = {
      val digest = new SHA256Digest()
      digest.update(message.toArray, 0, message.length)
      val out = new Array[Byte](digest.getDigestSize)
      digest.doFinal(out, 0)
      Sha256Hash(out)
    }

    override def apply(message: String): Sha256Hash = apply(message.getBytes)
  }

  implicit object ToBigInt extends ToBigInt[Sha256Hash] {
    override def apply(a: Sha256Hash): BigInt = BigInt(1, a.bytes)
  }

  implicit object Show extends Show[Sha256Hash] {
    override def show(f: Sha256Hash): String = s"Sha256Hash(${Hex.encode(f.bytes)})"
  }
}
