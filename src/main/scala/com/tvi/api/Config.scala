package com.tvi.api

import ciris._
import Config._

final case class Config(
    server:    Server
  , basicAuth: BasicAuth
  , apiAuth:   ApiAuth
)

object Config {
  def load: ConfigResult[api.Id, Config] =
    loadConfig(
        env[Int]("SERVER_PORT").orValue(8080)
      , env[String]("AUTH_USERNAME").orValue("admin")
      , env[String]("AUTH_PASSWORD").orValue("secure")
      , env[String]("API_KEY").orValue("secret")
    ) { case (port, username, password, apiKey) =>
      Config(
          Server(port)
        , BasicAuth(username, password)
        , ApiAuth(apiKey)
      )
    }

  final case class Server(port: Int)

  final case class BasicAuth(username: String, password: String)

  final case class ApiAuth(secret: String)
}
