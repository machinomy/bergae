package com.machinomy.bergae.crypto

object Base58Check {
  sealed abstract class Prefix(val value: Byte)
  // @todo Invalid prefixes
  object Prefix {
    object PrivateKey extends Prefix(128.toByte)
    object PublicKey extends Prefix(0.toByte)
    object Signature extends Prefix(27.toByte)
  }

  def checksum(bytes: Seq[Byte]): Array[Byte] = Digest[Sha256Hash](bytes).bytes.take(4)

  def encode(prefix: Prefix, bytes: Seq[Byte]): String = {
    val total = prefix.value +: bytes
    Base58.encode(total ++ checksum(total))
  }

  def decode(input: String): (Byte, Array[Byte]) = {
    val raw = Base58.decode(input)
    val versionAndHash = raw.dropRight(4)
    val checksum = raw.takeRight(4)
    require(checksum sameElements Base58Check.checksum(versionAndHash), s"invalid Base58Check data $input")
    (versionAndHash.head, versionAndHash.tail)
  }
}
