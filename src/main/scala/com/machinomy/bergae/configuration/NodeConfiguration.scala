package com.machinomy.bergae.configuration

import com.machinomy.bergae.crypto.{Base58Check, ECKey, ECPub}
import com.machinomy.xicity.Identifier

import com.typesafe.config.Config

import scala.collection.JavaConverters._

trait NodeConfiguration {
  def me: NodeConfiguration.Node
  def key: ECKey
  def seeds: Seq[NodeConfiguration.Node]
  def secret: String
}

object NodeConfiguration {
  case class Node(xicity: Identifier, pub: ECPub)
}

case class XicityNodeConfiguration(me: NodeConfiguration.Node, key: ECKey, seeds: Seq[NodeConfiguration.Node], secret: String) extends NodeConfiguration

object XicityNodeConfiguration {

  private def parseNode(config: Config): NodeConfiguration.Node = {
    val identifier = Identifier(config.getString("xicity"))
    val (_, pubBytes) = Base58Check.decode(config.getString("public"))
    val pub = ECPub(pubBytes)
    NodeConfiguration.Node(identifier, pub)
  }


  def apply(config: Config) = {
    val me = parseNode(config.getConfig("me"))
    val seeds = for (c <- config.getConfigList("seeds").asScala) yield parseNode(c)
    val (_, privateKeyBytes) = Base58Check.decode(config.getString("me.private"))
    val privateKey = BigInt(privateKeyBytes)
    val publicKey = ECKey(privateKey, me.pub)
    val secret = config.getString("secret")

    new XicityNodeConfiguration(me, publicKey, seeds, secret)
  }

}