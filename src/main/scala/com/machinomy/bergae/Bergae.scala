package com.machinomy.bergae

import akka.actor.{ActorRef, ActorSystem}

import com.machinomy.bergae.configuration.NodeConfiguration
import com.machinomy.bergae.storage.Storage

object Bergae {

  def node(configuration: NodeConfiguration, storage: Storage): ActorRef = {

    implicit val system = ActorSystem("bergae")

    val props = Node.props(configuration, storage)
    system actorOf props
  }

}
