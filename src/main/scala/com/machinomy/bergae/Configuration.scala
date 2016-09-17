package com.machinomy.bergae

import java.io.File

import com.machinomy.bergae.Configuration.RedisConfiguration
import com.machinomy.bergae.crypto.{Base58Check, ECKey, ECPub}
import com.machinomy.xicity.Identifier
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._

case class Configuration(me: Configuration.Node, key: ECKey, seeds: Seq[Configuration.Node], redis: RedisConfiguration)

object Configuration {
  case class Node(xicity: Identifier, pub: ECPub)
  case class RedisConfiguration(host: String, port: Int)

  def load(): Configuration = load(ConfigFactory.load())

  def load(file: File): Configuration = load(ConfigFactory.parseFile(file))

  def load(config: Config): Configuration = {
    def parseNode(config: Config): Node = {
      val identifier = Identifier(config.getString("xicity"))
      val (_, pubBytes) = Base58Check.decode(config.getString("pub"))
      val pub = ECPub(pubBytes)
      Node(identifier, pub)
    }

    val me = parseNode(config.getConfig("me"))
    val seeds = for (c <- config.getConfigList("seeds").asScala) yield parseNode(c)
    val (_, privBytes) = Base58Check.decode(config.getString("me.priv"))
    val priv = BigInt(privBytes)
    val key = ECKey(priv, me.pub)
    val redis = config.getConfig("redis")
    val redisHost = redis.getString("host")
    val redisPort = redis.getInt("port")
    val redisConfiguration = RedisConfiguration(redisHost, redisPort)
    Configuration(me, key, seeds, redisConfiguration)
  }
}
