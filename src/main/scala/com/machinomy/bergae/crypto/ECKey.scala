package com.machinomy.bergae.crypto

import java.security.SecureRandom

import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ECKeyGenerationParameters, ECPrivateKeyParameters, ECPublicKeyParameters}

case class ECKey(priv: BigInt, pub: ECPub)

object ECKey {
  def apply(): ECKey = ECKey(EllipticCurve.secureRandom)

  def apply(secureRandom: SecureRandom): ECKey = {
    val generator = new ECKeyPairGenerator()
    val keygenParams = new ECKeyGenerationParameters(EllipticCurve.CURVE, secureRandom)
    generator.init(keygenParams)
    val keypair = generator.generateKeyPair()
    val privParams = keypair.getPrivate.asInstanceOf[ECPrivateKeyParameters]
    val pubParams = keypair.getPublic.asInstanceOf[ECPublicKeyParameters]
    val priv = privParams.getD
    val pub = ECPub(EllipticCurve.CURVE.getCurve.decodePoint(pubParams.getQ.getEncoded(true)))
    ECKey(priv, pub)
  }

  def apply(priv: BigInt): ECKey = {
    val pub = ECPub.fromPriv(priv)
    ECKey(priv, pub)
  }
}
