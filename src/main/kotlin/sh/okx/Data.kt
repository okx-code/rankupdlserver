package sh.okx

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
class Data(val key: String, val users: List<SpigotUser>)

@Serializable
class SpigotUser(val spigotId: Int, val spigotName: String, @Contextual val buyTime: Instant, val buyPricePennies: Int)