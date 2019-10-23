package com.tvi.api

import java.io.File
import java.time.ZonedDateTime
import java.util.UUID

import com.tvi.api.Domain._
import io.circe.{Codec => CirceCodec, _}
import io.circe.generic.semiauto._
import kantan.csv._
import kantan.csv.java8._
import kantan.csv.generic._
import tapir._
import tapir.json.circe._
import tapir.model.UsernamePassword

import scala.util.control.NoStackTrace

object Endpoints {
  import Implicits._

  sealed abstract class StatusError(message: String) extends Exception with NoStackTrace

  case object NoContent extends StatusError("No Content")
  case object Unauthorized extends StatusError("Unauthorized")

  val updateTariff: Endpoint[(UsernamePassword, Tariff), String, Unit, Nothing] =
    endpoint
      .name("Update tariff")
      .summary("Updates service fees for charge sessions")
      .post
      .in(auth.basic)
      .in("tariffs")
      .in(
        jsonBody[Tariff]
          .description("Value to persist")
          .example(Tariff(0.1, None, 0.3, Some(ZonedDateTime.now.plusDays(1))))
          .validate(nonEmptyAmount("energy").contramap(_.energy))
          .validate(nonEmptyAmount("parking").asOptionElement.contramap(_.parking))
          .validate(serviceAmount("service", 0.5).contramap(_.service))
          .validate(dateInFuture("startsAt").asOptionElement.contramap(_.startsAt))
      )
      .errorOut(stringBody)

  val insertChargeSession: Endpoint[(String, ChargeSession), String, Unit, Nothing] =
    endpoint
      .name("Create charge session")
      .summary("Creates charge session sent by charge point")
      .post
      .in(auth.apiKey(header[String]("X-Auth-Token")))
      .in("sessions")
      .in(
        jsonBody[ChargeSession]
          .description("Value to persist")
          .example(ChargeSession(
              UUID.randomUUID().toString
            , ZonedDateTime.now.minusHours(1)
            , ZonedDateTime.now.minusMinutes(5)
            , 0.5
          ))
          .validate(identifier("driverId").contramap(_.driverId))
          .validate(dateInPast("startedAt").contramap(_.startedAt))
          .validate(dateInPast("endedAt").contramap(_.endedAt))
          .validate(datesRange("startedAt", "endedAt").contramap(data => (data.startedAt, data.endedAt)))
          .validate(nonEmptyAmount("consumed").contramap(_.consumed))
      )
      .errorOut(stringBody)

  val exportChargeSessions: Endpoint[String, String, List[ChargeSessionOverview], Nothing] =
    endpoint
      .name("Export charge sessions overview")
      .summary("Downloads charge sessions overview file")
      .get
      // TODO: Use OAuth or similar and access_token to fetch driverId instead
      .in(
        path[String].validate(identifier("driverId")).name("driverId")
      )
      .in("sessions")
      .in("export.csv")
      .out(
        binaryBody[List[ChargeSessionOverview]]
          .and(header("Content-Type", "text/csv"))
          .and(header("Content-Disposition", """attachment; filename="export.csv""""))
      )
      .errorOut(stringBody)

  private object Implicits {
    implicit val tariffCodec:  CirceCodec[Tariff]        = deriveCodec
    implicit val sessionCodec: CirceCodec[ChargeSession] = deriveCodec

    implicit val overviewHeaderEncoder: HeaderEncoder[ChargeSessionOverview] =
      HeaderEncoder.caseEncoder(
          "Session started"
        , "Session ended"
        , "Energy consumer, kW"
        , "Energy fee, per kWh"
        , "Parking fee, per hour"
        , "Service fee, perc."
        , "Total price"
        , "Total service fee"
      )(ChargeSessionOverview.unapply)
    implicit val overviewCsvCodec: Codec[List[ChargeSessionOverview], MediaType.OctetStream, File] =
      csvFileCodec[ChargeSessionOverview](CsvConfiguration.rfc.withHeader)

    private def csvFileCodec[T: RowEncoder: HeaderEncoder: HeaderDecoder](config: CsvConfiguration) = {
      import kantan.csv.ops._

      Codec.fileCodec
        .map[List[T]] {
        _
          .asCsvReader[T](config)
          .toList
          .map(_.toTry.get)
      }(list =>
        if (list.isEmpty) throw NoContent
        else {
          val file = File.createTempFile(s"export", ".csv")
          file.writeCsv(list, config)
          file
        }
      )
    }
  }

  private def dateInFuture(field: String): Validator[ZonedDateTime] =
    Validator.custom(
        _.isAfter(ZonedDateTime.now)
      , s"[$field] only future dates are acceptable"
    )

  private def dateInPast(field: String): Validator[ZonedDateTime] =
    Validator.custom(
        !_.isAfter(ZonedDateTime.now)
      , s"`$field` no future dates are acceptable"
    )

  private def datesRange(startField: String, endField: String): Validator[(ZonedDateTime, ZonedDateTime)] =
    Validator.custom(
        {
          case (startValue, endValue) => !(startValue isAfter endValue)
        }
      , s"`$startField` expected to not be greater then `$endField`"
    )

  private def identifier(field: String): Validator[String] =
    Validator.custom(
        str => str.nonEmpty & str.matches("""^\S+$""")
      , s"`$field` empty or invalid string can't be used as identifier"
    )

  private def nonEmptyAmount(field: String): Validator[Double] =
    Validator.custom(
        _ > 0
      , s"`$field` should be positive non-zero value"
    )

  private def serviceAmount(field: String, max: Double): Validator[Double] =
    Validator.custom(
        value => value > 0 && value <= max
      , s"`$field` should be in a range (0, $max]"
    )
}
