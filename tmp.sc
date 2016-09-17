import com.machinomy.bergae.Messaging
import com.machinomy.bergae.crypto.{ECKey, EllipticCurve}

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

val key = ECKey()
val time = 0L
val txt = time.toString.getBytes

val nop = Messaging.Nop(1)
val message = Messaging.signed(nop, key)
import Messaging._
val jsonString = message.asJson.noSpaces

val a = decode[Signed](jsonString)
