package com.machinomy.bergae.crypto

trait ToBigInt[A] {
  def apply(a: A): BigInt
}
