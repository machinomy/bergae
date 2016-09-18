import com.machinomy.bergae._
import java.io.File
import java.util.UUID

import akka.actor.ActorSystem

implicit val actorSystem = ActorSystem("foo")
val configuration = Configuration.load(new File("./a.json"))
val storage = new Storage(configuration)

val person = Storage.AddPerson("A", "B", "C", "tax")

val uuid = UUID.randomUUID()
storage.append(uuid, person)
val strings = storage.get(uuid)

