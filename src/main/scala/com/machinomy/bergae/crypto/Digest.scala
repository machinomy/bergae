package com.machinomy.bergae.crypto

trait Digest[A] {
  def apply(message: Seq[Byte]): A
  def apply(message: String): A
}

object Digest {
  def apply[A](message: Seq[Byte])(implicit digest: Digest[A]): A = digest.apply(message)
  def apply[A](message: String)(implicit digest: Digest[A]): A = digest.apply(message)
}
