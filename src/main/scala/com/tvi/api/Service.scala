package com.tvi.api

import java.math.{BigDecimal, RoundingMode}
import java.time.temporal.ChronoUnit
import java.time.{Duration, ZonedDateTime}

import cats.implicits._
import cats.effect.Sync
import cats.Parallel
import io.scalaland.chimney.dsl._

import Domain._
import Storage._

class Service[F[_]: Sync: Parallel](
    tariffs: TariffStorage[F]
  , sessions: ChargeSessionStorage[F]
  , calculator: Service.FeesCalculator
) {
  def saveTariff(value: Tariff): F[Unit] = {
    val startsAt = value.startsAt.getOrElse(ZonedDateTime.now())
    tariffs.put(startsAt, value.transformInto[TariffStorage.Entity])
  }

  def recordChargeSession(value: ChargeSession): F[Unit] =
    for {
      tariff <- tariffs.get(value.startedAt)
      entity = {
        val (totalPrice, serviceFee) = calculator.calculate(value, tariff)

        value.into[ChargeSessionStorage.Entity]
          .withFieldConst(_.totalPrice, totalPrice)
          .withFieldConst(_.totalServiceFee, totalPrice + serviceFee)
          .transform
      }

      _ <- sessions.put(value.driverId, entity)
    } yield ()

  def overview(driverId: DriverId): F[List[ChargeSessionOverview]] =
    sessions.get(driverId)
      .flatMap {
        _.parTraverse { session =>
          tariffs.get(session.startedAt)
            .map { tariff =>
              session.into[ChargeSessionOverview]
                .withFieldConst(_.energyFee, tariff.energy)
                .withFieldConst(_.parkingFee, tariff.parking)
                .withFieldConst(_.serviceFee, tariff.service)
                .transform
            }
        }
      }
}

object Service {
  def default[F[_]: Sync: Parallel]: F[Service[F]] =
    for {
      tariffs  <- TariffStorage.inMemory[F](TariffStorage.Entity(0.214, None, 0.2))
      sessions <- ChargeSessionStorage.inMemory[F]
    } yield {
      new Service(tariffs, sessions, FeesCalculator.default)
    }

  class FeesCalculator(feeRounder: FeeRoundingStrategy, timeRounder: TimeRoundingStrategy) {
    type TotalPrice = Double
    type ServiceFee = Double

    def calculate(session: ChargeSession, tariff: TariffStorage.Entity): (TotalPrice, ServiceFee) = {
      val energyPrice = session.consumed * tariff.energy
      val parkingFee  = tariff.parking.map { fee =>
        val duration = timeRounder.round(Duration.between(session.startedAt, session.endedAt)).toHours
        fee * duration
      }

      val totalPrice = energyPrice + parkingFee.getOrElse(0.0)
      (feeRounder.round(totalPrice), feeRounder.round(totalPrice * tariff.service))
    }
  }

  object FeesCalculator {
    def default: FeesCalculator =
      new FeesCalculator(
          new FeeRoundingStrategy(4, RoundingMode.CEILING)
        , new TimeRoundingStrategy(true)
      )
  }

  class FeeRoundingStrategy(scale: Int, mode: RoundingMode) {
    def round(value: Double): Double =
      new BigDecimal(value).setScale(scale, mode).doubleValue()
  }

  class TimeRoundingStrategy(ceiling: Boolean) {
    def round(value: Duration): Duration = {
      if (ceiling && value.getSeconds % 3600 != 0)
        value.truncatedTo(ChronoUnit.HOURS).plusHours(1)
      else
        value.truncatedTo(ChronoUnit.HOURS)
    }
  }
}
