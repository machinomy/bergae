import com.machinomy.bergae._
import java.io.File
import java.util.UUID

val configuration = Configuration.load(new File("./a.json"))
val storage = new Storage(configuration)

val person = Storage.AddPerson("A", "B", "C", 10, 20, "tax", "2016-09-04", "space")

val uuid = UUID.randomUUID()
storage.append(uuid, person)
val strings = storage.get(uuid)

