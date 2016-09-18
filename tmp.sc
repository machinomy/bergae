import com.machinomy.bergae._
import java.io.File
import java.util.UUID

import com.machinomy.bergae.crypto.{Base58Check, ECKey}

val key = ECKey()
val pub = key.pub
val priv = key.priv

Base58Check.encode(Base58Check.Prefix.PublicKey, pub.toByteArray)

Base58Check.encode(Base58Check.Prefix.PrivateKey, priv.toByteArray)
