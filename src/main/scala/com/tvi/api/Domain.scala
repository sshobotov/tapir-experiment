package com.tvi.api

import java.time.ZonedDateTime

object Domain {
  type DriverId         = String
  type PricePerKiloWatt = Double
  type PricePerHour     = Double
  type KiloWatt         = Double
  type Percentage       = Double

  final case class Tariff(
      energy:   PricePerKiloWatt
    , parking:  Option[PricePerHour]
    , service:  Percentage
    , startsAt: Option[ZonedDateTime]
  )

  final case class ChargeSession(
      driverId:  DriverId
    , startedAt: ZonedDateTime
    , endedAt:   ZonedDateTime
    , consumed:  KiloWatt
  )

  final case class ChargeSessionOverview(
      startedAt:       ZonedDateTime
    , endedAt:         ZonedDateTime
    , consumed:        KiloWatt
    , energyFee:       PricePerKiloWatt
    , parkingFee:      Option[PricePerHour]
    , serviceFee:      Percentage
    , totalPrice:      Double
    , totalServiceFee: Double
  )
}
