package com.machinomy.bergae.crypto

import java.io.ByteArrayOutputStream

import org.bouncycastle.asn1.{ASN1InputStream, ASN1Integer, DERSequenceGenerator, DLSequence}

case class ECSignature(r: BigInt, s: BigInt)

object ECSignature {
  def encode(signature: ECSignature): Array[Byte] = {
    val bos = new ByteArrayOutputStream(72)
    val seq = new DERSequenceGenerator(bos)
    seq.addObject(new ASN1Integer(signature.r.bigInteger))
    seq.addObject(new ASN1Integer(signature.s.bigInteger))
    seq.close()
    bos.toByteArray
  }

  def decode(input: Array[Byte]): ECSignature = {
    val decoder = new ASN1InputStream(input)
    val seq = decoder.readObject().asInstanceOf[DLSequence]
    val r = seq.getObjectAt(0).asInstanceOf[ASN1Integer].getPositiveValue
    val s = seq.getObjectAt(1).asInstanceOf[ASN1Integer].getPositiveValue
    decoder.close()
    ECSignature(r, s)
  }
}
