package com.machinomy.bergae.crypto

import cats.Show
import org.bouncycastle.math.ec.{ECPoint, FixedPointCombMultiplier}

case class ECPub(point: ECPoint) {
  def toByteArray: Array[Byte] = point.getEncoded(true)
}

object ECPub {
  def fromPriv(priv: BigInt): ECPub = {
    val curve = EllipticCurve.CURVE
    val effectivePriv: BigInt =
      if (priv.bitLength > curve.getN.bitLength) {
        priv.mod(curve.getN)
      } else {
        priv
      }
    ECPub(new FixedPointCombMultiplier().multiply(curve.getG, effectivePriv.bigInteger))
  }

  def apply(bytes: Seq[Byte]): ECPub = {
    val point = EllipticCurve.CURVE.getCurve.decodePoint(bytes.toArray)
    ECPub(point)
  }

  implicit object Show extends Show[ECPub] {
    override def show(f: ECPub): String = s"ECPub(${Hex.encode(f.point.getEncoded(true))})"
  }
}

