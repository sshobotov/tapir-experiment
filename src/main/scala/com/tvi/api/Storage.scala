package com.tvi.api

import java.time.ZonedDateTime

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import Domain.DriverId

import scala.collection.immutable.SortedMap

object Storage {
  trait TariffStorage[F[_]] {
    def put(date: ZonedDateTime, value: TariffStorage.Entity): F[Unit]

    def get(date: ZonedDateTime): F[TariffStorage.Entity]
  }

  object TariffStorage {
    final case class Entity(
        energy:   Double
      , parking:  Option[Double]
      , service:  Double
    )

    def inMemory[F[_]: Sync](default: Entity): F[TariffStorage[F]] = {
      val ordering = Ordering.fromLessThan[ZonedDateTime](_ isBefore _).reverse

      for {
        ref <- Ref.of[F, SortedMap[ZonedDateTime, Entity]](SortedMap.empty(ordering))
      } yield {
        new TariffStorage[F] {
          override def put(date: ZonedDateTime, value: TariffStorage.Entity): F[Unit] =
            ref.update(_.updated(date, value))

          override def get(date: ZonedDateTime): F[TariffStorage.Entity] =
            ref.get
              .map {
                _
                  .find { case (key, _) => key.isBefore(date) }
                  .map { case (_, value) => value }
                  .getOrElse(default)
              }
        }
      }
    }
  }

  trait ChargeSessionStorage[F[_]] {
    def put(driverId: DriverId, value: ChargeSessionStorage.Entity): F[Unit]

    def get(driverId: DriverId): F[List[ChargeSessionStorage.Entity]]
  }

  object ChargeSessionStorage {
    final case class Entity(
        startedAt:       ZonedDateTime
      , endedAt:         ZonedDateTime
      , consumed:        Double
      , totalPrice:      Double
      , totalServiceFee: Double
    )

    def inMemory[F[_]: Sync]: F[ChargeSessionStorage[F]] =
      for {
        ref <- Ref.of[F, Map[DriverId, List[ChargeSessionStorage.Entity]]](Map.empty)
      } yield {
        new ChargeSessionStorage[F] {
          override def put(driverId: DriverId, value: ChargeSessionStorage.Entity): F[Unit] =
            ref.update { index =>
              index.updated(driverId, index.getOrElse(driverId, List.empty) :+ value)
            }

          override def get(driverId: DriverId): F[List[ChargeSessionStorage.Entity]] =
            ref.get.map(_.getOrElse(driverId, List.empty))
        }
      }
  }
}
