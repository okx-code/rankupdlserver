package sh.okx

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp

fun main() {
  val config = HoconApplicationConfig(ConfigFactory.parseFile(File("rankup.conf")))
  embeddedServer(Netty, port = 8031, host = "0.0.0.0") {
    install(ContentNegotiation) { json(Json { serializersModule = SerializersModule { contextual(InstantAsLongSerializer) } }) }

    setupRouter(config.property("key").getString(), setupHikari(config))
  }.start(wait = true)
}

fun setupHikari(applicationConfig: HoconApplicationConfig): HikariDataSource {
  val config = HikariConfig()
  config.jdbcUrl = "jdbc:mysql://${applicationConfig.property("host").getString()}:${applicationConfig.property("port").getString()}/${applicationConfig.property("database").getString()}"
  config.username = applicationConfig.property("username").getString()
  config.password = applicationConfig.property("password").getString()
  return HikariDataSource(config)
}

fun Application.setupRouter(key: String, dataSource: HikariDataSource) {
  dataSource.connection.use {
    it.createStatement()
      .execute("CREATE TABLE IF NOT EXISTS buyers (spigot_id INT PRIMARY KEY, spigot_name VARCHAR(255) NOT NULL, buy_time TIMESTAMP DEFAULT NOW() NOT NULL, buy_price DECIMAL(10,2) NOT NULL, discord_id BIGINT NULL)");
  }

  routing {
    post("/data") {
      val data: Data
      try {
        data = call.receive()
      } catch (ex: Exception) {
        ex.printStackTrace()
        return@post
      }

      if (data.key != key) {
        call.respond(HttpStatusCode.Forbidden)
        return@post
      }

      dataSource.connection.use {
        val statement = it.prepareStatement("INSERT IGNORE INTO buyers (spigot_id, spigot_name, buy_time, buy_price) VALUES (?, ?, ?, ?)")
        for (user in data.users) {
          statement.setInt(1, user.spigotId)
          statement.setString(2, user.spigotName)
          statement.setTimestamp(3, Timestamp.from(user.buyTime))
          statement.setBigDecimal(4, BigDecimal.valueOf(user.buyPricePennies.toLong(), 2))
          statement.addBatch()
        }
        statement.executeBatch()
      }
      log.info("Inserted ${data.users.size} users")
      call.respond(HttpStatusCode.NoContent)
    }
  }
}