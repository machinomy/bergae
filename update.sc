import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import com.machinomy.bergae.{Configuration, Bergae, Messaging, Node}

val arguments = Bergae.Arguments(new File("b.json"))
val configuration = Configuration load arguments.config
val system = ActorSystem("bergae")
val props = Node props configuration
val node = system actorOf props

val uuid = UUID.randomUUID()
node ! Node.Update(uuid, "foo")

node ! Node.Update(UUID.randomUUID(), "blah")
