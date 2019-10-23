package com.tvi.api

import cats.MonadError
import cats.implicits._
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource, Sync}
import org.http4s.{EntityBody, HttpRoutes}
import org.http4s.server.Router
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import tapir.Endpoint
import tapir.docs.openapi._
import tapir.model.UsernamePassword
import tapir.openapi.circe.yaml._
import tapir.server.http4s._
import tapir.swagger.http4s.SwaggerHttp4s

object Application extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val resources =
      for {
        config  <- Resource.liftF(IO(Config.load.orThrow))
        service <- Resource.liftF(Service.default[IO])
        _       <- serverResource(config, service)
      } yield config

    resources.use { config =>
      for {
        _ <- IO(scribe.info(s"Started server on port ${config.server.port}")) >> IO.never
      } yield ExitCode.Success
    } <* IO(scribe.info("Stopped server"))
  }

  private def serverResource(config: Config, service: Service[IO]) =
    BlazeServerBuilder[IO]
      .bindHttp(config.server.port, "localhost")
      .withHttpApp(Router(
          "/docs" -> new SwaggerHttp4s(openApiDocs.toYaml).routes[IO]
        , "/"     -> routes(config, service)
      ).orNotFound)
      .resource

  private def routes(config: Config, service: Service[IO]) =
    Endpoints.updateTariff.toNonFailingRoutes((service.saveTariff _).authorized(config.basicAuth)) <+>
    Endpoints.insertChargeSession.toNonFailingRoutes((service.recordChargeSession _).authorized(config.apiAuth)) <+>
    Endpoints.exportChargeSessions.toNonFailingRoutes(service.overview)

  private def openApiDocs =
    List(
        Endpoints.updateTariff
      , Endpoints.insertChargeSession
      , Endpoints.exportChargeSessions
    )
      .toOpenAPI("TVI endpoints", "1.0.0")

  private implicit class EndpointsPimp[I, O, F[_]](val e: Endpoint[I, String, O, EntityBody[F]]) extends AnyVal {
    def toNonFailingRoutes(logic: I => F[O])(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] =
      e.toRoutes {
        logic(_)
          .map(_.asRight[String])
          .recover {
            case err: Endpoints.StatusError => err.getMessage.asLeft[O]
          }
          .onError {
            case err => F.delay(scribe.error("Unexpected error", err))
          }
      }
  }

  private implicit class ServiceActionPimp[T, R, F[_]](val action: T => F[R]) {
    def authorized(config: Config.BasicAuth)(in: (UsernamePassword, T))(implicit M: MonadError[F, Throwable]): F[R] = {
      val (credentials, value) = in
      if (credentials.username == config.username && credentials.password.contains(config.password))
        action(value)
      else
        M.raiseError(Endpoints.Unauthorized)
    }

    def authorized(config: Config.ApiAuth)(in: (String, T))(implicit M: MonadError[F, Throwable]): F[R] = {
      val (apiKey, value) = in
      if (apiKey == config.secret) action(value)
      else M.raiseError(Endpoints.Unauthorized)
    }
  }
}
