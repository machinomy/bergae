package com.machinomy.bergae

import java.util.UUID

import com.redis.RedisClient

class Storage(configuration: Configuration) {
  val client = new RedisClient(configuration.redis.host, configuration.redis.port)

  def append(uuid: UUID, string: String): Unit = {
    client.rpush(uuid, string)
  }

  def height: Long = client.get("height").map(_.toLong).getOrElse(0)

  def height_=(value: Long) = client.set("height", value)
}
