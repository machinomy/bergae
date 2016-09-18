package com.machinomy.bergae

import java.io.File

import com.machinomy.bergae.Configuration.{HttpConfiguration, RedisConfiguration}
import com.machinomy.bergae.crypto.{Base58Check, ECKey, ECPub}
import com.machinomy.xicity.Identifier
import com.typesafe.config.{Config, ConfigFactory}
import org.scalacheck.Prop.False

import scala.collection.JavaConverters._
import scala.util.Try

case class Configuration(me: Configuration.Node,
                         key: ECKey,
                         seeds: Seq[Configuration.Node],
                         redis: RedisConfiguration,
                         httpOpt: Option[HttpConfiguration],
                         secret: String)

object Configuration {
  case class Node(xicity: Identifier, pub: ECPub)
  case class RedisConfiguration(host: String, port: Int)
  case class HttpConfiguration(name: String, port: Int)

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

    val httpConfiguration = Try(config.getConfig("http")).toOption.map { httpConfiguration =>
      val httpName = httpConfiguration.getString("name")
      val httpPort = httpConfiguration.getInt("port")
      HttpConfiguration(httpName, httpPort)
    }

    val secret = config.getString("secret")
    Configuration(me, key, seeds, redisConfiguration, httpConfiguration, secret)
  }
}
