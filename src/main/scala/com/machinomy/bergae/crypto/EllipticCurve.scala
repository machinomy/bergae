package com.machinomy.bergae.crypto

import java.security.SecureRandom

import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.{ECDomainParameters, ECPrivateKeyParameters, ECPublicKeyParameters}
import org.bouncycastle.crypto.signers.{ECDSASigner, HMacDSAKCalculator}
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import org.bouncycastle.math.ec.{ECAlgorithms, ECPoint, FixedPointUtil}

object EllipticCurve {
  val CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1")

  FixedPointUtil.precompute(CURVE_PARAMS.getG, 12)

  val CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve, CURVE_PARAMS.getG, CURVE_PARAMS.getN, CURVE_PARAMS.getH)
  val HALF_CURVE_ORDER = CURVE_PARAMS.getN.shiftRight(1)

  val secureRandom = new SecureRandom()

  def sign(message: Seq[Byte], key: ECKey)(implicit digest: Digest[Sha256Hash]): ECSignature = {
    val hash = digest(message)
    sign(hash, key)
  }

  def sign(digest: Sha256Hash, key: ECKey): ECSignature = {
    val signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()))
    val privateKeyParameters = new ECPrivateKeyParameters(key.priv.bigInteger, CURVE)
    signer.init(true, privateKeyParameters)
    val Array(r, sRaw) = signer.generateSignature(digest.bytes)
    val s = if (sRaw.compareTo(HALF_CURVE_ORDER) > 0) CURVE.getN.subtract(sRaw) else sRaw
    ECSignature(r, s)
  }

  def verify(digest: Sha256Hash, signature: ECSignature, pub: ECPub): Boolean = {
    assert(signature.r >= 1)
    assert(signature.r <= CURVE.getN)
    assert(signature.s >= 1)
    assert(signature.s <= CURVE.getN)

    val signer = new ECDSASigner()
    val point = pub.point.getEncoded(true)
    val params = new ECPublicKeyParameters(CURVE.getCurve.decodePoint(point), CURVE)
    signer.init(false, params)
    signer.verifySignature(digest.bytes, signature.r.bigInteger, signature.s.bigInteger)
  }

  def verify(message: Seq[Byte], signature: ECSignature, pub: ECPub)(implicit digest: Digest[Sha256Hash]): Boolean = {
    val hash = digest(message)
    verify(hash, signature, pub)
  }

  def recoverFromSignature(recId: Int, signature: ECSignature, digest: Sha256Hash)(implicit toBigInt: ToBigInt[Sha256Hash]): Option[ECPub] = {
    def decompressKey(xBN: BigInt, yBit: Boolean): ECPoint = {
      val x9 = new X9IntegerConverter()
      val compEnc = x9.integerToBytes(xBN.bigInteger, 1 + x9.getByteLength(CURVE.getCurve))
      compEnc.update(0, if (yBit) 0x03 else  0x02)
      CURVE.getCurve.decodePoint(compEnc)
    }

    assert(recId >= 0 && recId <= 3)
    assert(signature.r.signum >= 0)
    assert(signature.s.signum >= 0)

    val n = CURVE.getN
    val i = BigInt(recId / 2)
    val x = signature.r + i * n
    val prime = SecP256K1Curve.q
    if (x >= prime) {
      None
    } else {
      val R = decompressKey(x, (recId & 1) == 1)
      if (!R.multiply(n).isInfinity) {
        None
      } else {
        val e = toBigInt(digest)
        val eInv = (0 - e).mod(n)
        val rInv = signature.r.modInverse(n)
        val srInv = (rInv * signature.s).mod(n)
        val eInvrInv = (rInv * eInv).mod(n)
        val q = ECAlgorithms.sumOfTwoMultiplies(CURVE.getG, eInvrInv.bigInteger, R, srInv.bigInteger)
        Some(ECPub(q))
      }
    }
  }
}
