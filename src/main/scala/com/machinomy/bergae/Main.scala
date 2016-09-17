package com.machinomy.bergae

import java.io.File

import akka.actor.{ActorRef, ActorSystem}

object Main extends App {
  case class Arguments(config: File = new File("./application.json"))

  var node: ActorRef = _

  parse(args) foreach { arguments =>
    val configuration = Configuration load arguments.config
    val system = ActorSystem("bergae")
    val props = Node props configuration
    node = system actorOf props
  }

  def parse(args: Array[String]): Option[Arguments] = {
    val parser = new scopt.OptionParser[Arguments]("bergae") {
      head("bergae - blockless blockchain")

      opt[File]('c', "config")
        .required()
        .valueName("<file>")
        .action((f, c) => c.copy(config = f))
        .text("config is a required property")
    }
    parser.parse(args, Arguments())
  }
}
