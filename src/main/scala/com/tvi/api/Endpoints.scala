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

object Endpoints {
  import Implicits._

  case object NoContent extends Exception("No Content")

  val updateTariff: Endpoint[Tariff, Unit, Unit, Nothing] =
    endpoint
      .name("Update tariff")
      .summary("Updates service fees for charge sessions")
      .post
      .in("tariff")
      .in(
        jsonBody[Tariff]
          .description("Value to persist")
          .example(Tariff(0.1, None, 0.3, Some(ZonedDateTime.now.plusDays(1))))
          .validate(nonEmptyAmount.contramap(_.energy))
          .validate(nonEmptyAmount.asOptionElement.contramap(_.parking))
          .validate(nonEmptyAmount.and(Validator.max(0.5)).contramap(_.service))
          .validate(dateInFuture.asOptionElement.contramap(_.startsAt))
      )

  val insertChargeSession: Endpoint[ChargeSession, Unit, Unit, Nothing] =
    endpoint
      .name("Create charge session")
      .summary("Creates charge session sent by charge point")
      .post
      .in("session")
      .in(
        jsonBody[ChargeSession]
          .description("Value to persist")
          .example(ChargeSession(
              UUID.randomUUID().toString
            , ZonedDateTime.now.minusHours(5)
            , ZonedDateTime.now.minusMinutes(5)
            , 55
          ))
          .validate(identifier.contramap(_.driverId))
          .validate(dateInPast.contramap(_.startedAt))
          .validate(dateInPast.contramap(_.endedAt))
          .validate(datesRange.contramap(data => (data.startedAt, data.endedAt)))
          .validate(nonEmptyAmount.contramap(_.consumed))
      )

  val exportChargeSessions: Endpoint[String, Unit, List[ChargeSessionOverview], Nothing] =
    endpoint
      .name("Export charge sessions overview")
      .summary("Downloads charge sessions overview file")
      .get
      // TODO: Use OAuth or similar and access_token to fetch driverId instead
      .in(
        path[String].validate(identifier)
      )
      .in("session")
      .in("export.csv")
      .out(
        binaryBody[List[ChargeSessionOverview]]
          .and(header("Content-Type", "text/csv"))
          .and(header("Content-Disposition", "attachment"))
      )

  private object Implicits {
    implicit val tariffCodec:  CirceCodec[Tariff]        = deriveCodec
    implicit val sessionCodec: CirceCodec[ChargeSession] = deriveCodec

    // TODO: Provide CSV header derivation
    implicit val overviewCsvCodec: Codec[List[ChargeSessionOverview], MediaType.OctetStream, File] =
      csvFileCodec[ChargeSessionOverview](CsvConfiguration.rfc.withoutHeader)
  }

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

  private def dateInFuture: Validator[ZonedDateTime] =
    Validator.custom(
        _.isAfter(ZonedDateTime.now)
      , "Only future dates are acceptable"
    )

  private def dateInPast: Validator[ZonedDateTime] =
    Validator.custom(
        !_.isAfter(ZonedDateTime.now)
      , "No future dates are acceptable"
    )

  private def datesRange: Validator[(ZonedDateTime, ZonedDateTime)] =
    Validator.custom(
        dates => !(dates._1 isAfter dates._2)
      , "Later date came first in a dates range"
    )

  private def identifier: Validator[String] =
    Validator.minLength(1)
      .and(Validator.pattern("""^\S+$"""))

  private def nonEmptyAmount: Validator[Double] =
    Validator.min(0.0, exclusive = true)
}
