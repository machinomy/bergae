import com.machinomy.bergae._
import java.io.File
import java.util.UUID

val configuration = Configuration.load(new File("./a.json"))
val storage = new Storage(configuration)

val person = Storage.Person("A", "B", "C", 10, 20, "tax", "2016-09-04", "space")

storage.append(UUID.randomUUID, person)
