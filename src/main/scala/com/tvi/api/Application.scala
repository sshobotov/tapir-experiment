package com.tvi.api

import cats.implicits._
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource, Sync}
import org.http4s.{EntityBody, HttpRoutes}
import org.http4s.server.Router
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import tapir.Endpoint
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import tapir.server.http4s._
import tapir.swagger.http4s.SwaggerHttp4s

object Application extends IOApp {
  val serverPort = 8080

  override def run(args: List[String]): IO[ExitCode] = {
    val resources =
      for {
        service <- Resource.liftF(Service.default[IO])
        _       <- serverResource(serverPort, service)
      } yield ()

    resources.use { _ =>
      for {
        _ <- IO(scribe.info(s"Started server on port $serverPort")) >> IO.never
      } yield ExitCode.Success
    } <* IO(scribe.info("Stopped server"))
  }

  private def serverResource(port: Int, service: Service[IO]) =
    BlazeServerBuilder[IO]
      .bindHttp(port, "localhost")
      .withHttpApp(Router(
          "/docs" -> new SwaggerHttp4s(openApiDocs.toYaml).routes[IO]
        , "/"     -> routes(service)
      ).orNotFound)
      .resource

  private def routes(service: Service[IO]) =
    Endpoints.updateTariff.toNonFailingRoutes(service.saveTariff) <+>
    Endpoints.insertChargeSession.toNonFailingRoutes(service.recordChargeSession) <+>
    Endpoints.exportChargeSessions.toNonFailingRoutes(service.overview)

  private def openApiDocs =
    List(
        Endpoints.updateTariff
      , Endpoints.insertChargeSession
      , Endpoints.exportChargeSessions
    )
      .toOpenAPI("TVI endpoints", "1.0.0")

  private implicit class EndpointsPimp[I, E, O, F[_]](val e: Endpoint[I, E, O, EntityBody[F]]) extends AnyVal {
    def toNonFailingRoutes(logic: I => F[O])(implicit F: Sync[F], cs: ContextShift[F]): HttpRoutes[F] =
      e.toRoutes(logic(_).map(_.asRight[E]))
  }
}
