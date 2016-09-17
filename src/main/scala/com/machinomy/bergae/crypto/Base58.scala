package com.machinomy.bergae.crypto

import java.math.BigInteger

import scala.language.implicitConversions

object Base58 {
  val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

  val map = ALPHABET.zipWithIndex.toMap

  def encode(input: Seq[Byte]): String =
    if (input.isEmpty) {
      ""
    } else {
      val big = new BigInteger(1, input.toArray)
      val builder = new StringBuilder

      def encode1(current: BigInteger): Unit = current match {
        case BigInteger.ZERO => ()
        case _ =>
          val Array(x, remainder) = current.divideAndRemainder(BigInteger.valueOf(58L))
          builder.append(ALPHABET.charAt(remainder.intValue))
          encode1(x)
      }
      encode1(big)
      input.takeWhile(_ == 0).map(_ => builder.append(ALPHABET.charAt(0)))
      builder.toString().reverse
    }

  def decode(input: String): Array[Byte] = {
    val zeroes = input.takeWhile(_ == '1').map(_ => 0:Byte).toArray
    val trim  = input.dropWhile(_ == '1').toList
    val decoded = trim.foldLeft(BigInteger.ZERO)((a, b) => a.multiply(BigInteger.valueOf(58L)).add(BigInteger.valueOf(map(b))))
    if (trim.isEmpty) zeroes else zeroes ++ decoded.toByteArray.dropWhile(_ == 0)
  }
}
