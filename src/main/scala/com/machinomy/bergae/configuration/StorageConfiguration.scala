package com.machinomy.bergae.configuration

import com.typesafe.config.Config

trait StorageConfiguration

case class RedisConfiguration(host: String, port: Int) extends StorageConfiguration

object RedisConfiguration {

  def apply(config: Config): RedisConfiguration = {
    val redis = config.getConfig("redis")
    val redisHost = redis.getString("host")
    val redisPort = redis.getInt("port")
    new RedisConfiguration(redisHost, redisPort)
  }

}