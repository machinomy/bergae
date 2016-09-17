package com.machinomy.bergae.crypto

object Hex {
  def encode(bytes: Seq[Byte]): String =
    bytes.map("%02x".format(_)).mkString
  def decode(string: String): Seq[Byte] =
    string.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
}
