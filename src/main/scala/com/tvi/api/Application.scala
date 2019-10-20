package com.tvi.api

import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import org.http4s.server.blaze.BlazeServerBuilder
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import tapir.server.http4s._
import tapir.swagger.http4s.SwaggerHttp4s

object Application extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val resources =
      for {
        service <- Resource.liftF(Service.default[IO])
        _       <- serverResource(8080, service)
      } yield ()

    resources.use { _ =>
      for {
        _ <- IO(scribe.info("Started server")) >> IO.never
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
    Endpoints.updateTariff.toRoutes(service.saveTariff(_).map(_.asRight[Unit])) <+>
      Endpoints.insertChargeSession.toRoutes(service.recordChargeSession(_).map(_.asRight[Unit]))

  private def openApiDocs =
    List(
        Endpoints.updateTariff
      , Endpoints.insertChargeSession
      , Endpoints.exportChargeSessions
    )
      .toOpenAPI("TVI endpoints", "1.0.0")
}
