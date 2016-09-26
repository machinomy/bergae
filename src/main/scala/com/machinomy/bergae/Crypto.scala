package com.machinomy.bergae

import com.machinomy.bergae.configuration.NodeConfiguration
import com.machinomy.bergae.crypto._

private[bergae] class Crypto(configuration: NodeConfiguration) {
  val secret: Array[Byte] = configuration.secret.getBytes

  def sign(message: Seq[Byte], key: ECKey)(implicit digest: Digest[Sha256Hash]): ECSignature = {
    val fullMessage = message ++ secret
    EllipticCurve.sign(fullMessage, key)
  }

  def verify(message: Seq[Byte], signature: ECSignature, pub: ECPub)(implicit digest: Digest[Sha256Hash]): Boolean = {
    val fullMessage = message ++ secret
    EllipticCurve.verify(fullMessage, signature, pub)
  }
}
