package com.tvi.api

import java.time.ZonedDateTime
import java.util.UUID

import com.tvi.api.Domain._
import io.circe.{Codec => CirceCodec, _}
import io.circe.generic.semiauto._
import tapir._
import tapir.json.circe._

object Endpoints {
  implicit val tariffCodec:  CirceCodec[Tariff]        = deriveCodec
  implicit val sessionCodec: CirceCodec[ChargeSession] = deriveCodec

  val updateTariff =
    endpoint
      .name("Update tariff")
      .summary("Updates service fees for charge sessions")
      .post
      .in("tariff")
      .in(
        jsonBody[Tariff]
          .description("Value to persist")
          .example(Tariff(0.1, None, 0.3, Some(ZonedDateTime.now.plusDays(1))))
          .validate(Validator.min(0.0, exclusive = true).contramap(_.energy))
          .validate(Validator.min(0.0, exclusive = true).asOptionElement.contramap(_.parking))
          .validate(Validator.min(0.0, exclusive = true).and(Validator.max(0.5)).contramap(_.service))
          .validate(dateInFuture.asOptionElement.contramap(_.startsAt))
      )

  val insertChargeSession =
    endpoint
      .name("Create charge session")
      .summary("Creates charge session sent by charge point")
      .post
      .in("session")
      .in(
        jsonBody[ChargeSession]
          .description("Value to persist")
          .example(ChargeSession(UUID.randomUUID().toString, ZonedDateTime.now.minusHours(5), ZonedDateTime.now.minusMinutes(5), 55))
          .validate(Validator.pattern("""\S+""").contramap(_.driverId))
          .validate(dateInPast.contramap(_.startedAt))
          .validate(dateInPast.contramap(_.endedAt))
          .validate(Validator.min(0.0, exclusive = true).contramap(_.consumed))
      )

  val exportChargeSessions =
    endpoint
      .name("Export charge sessions overview")
      .summary("Downloads charge sessions overview file")
      .get
      .in("session")
      .in("export.csv")
      .out(
        binaryBody[List[ChargeSessionOverview]]
      )

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
}
