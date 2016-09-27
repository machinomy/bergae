package com.machinomy.bergae

import akka.actor.{ActorRef, ActorSystem}

import com.machinomy.bergae.configuration.NodeConfiguration
import com.machinomy.bergae.storage.Storage

object Bergae {

  def node[T <: Storage.Operation](configuration: NodeConfiguration, storage: Storage[T]): ActorRef = {

    implicit val system = ActorSystem("bergae")

    val props = Node.props(configuration, storage)
    system actorOf props
  }

}
