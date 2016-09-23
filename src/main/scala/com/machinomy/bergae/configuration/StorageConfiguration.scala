package com.machinomy.bergae.configuration

import com.typesafe.config.Config

trait StorageConfiguration

case class RedisStorageConfiguration(host: String, port: Int) extends StorageConfiguration

object RedisStorageConfiguration {

  def apply(config: Config): RedisStorageConfiguration = {
    val redisHost = config.getString("host")
    val redisPort = config.getInt("port")
    new RedisStorageConfiguration(redisHost, redisPort)
  }

}