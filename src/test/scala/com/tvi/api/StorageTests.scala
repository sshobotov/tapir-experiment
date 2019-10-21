package com.tvi.api

import java.time.ZonedDateTime

import cats.effect.IO
import utest._

import Storage._

object StorageTests extends TestSuite {
  val tests = Tests {
    test("TariffStorage") {
      def instance = TariffStorage.inMemory[IO](TariffStorage.Entity(0, None, 0))

      val entity = TariffStorage.Entity(10, Some(2), 0.3)

      test("put should persist value") {
        val keyDate = ZonedDateTime.now()
        val expects = entity
        val results = (
          for {
            subject <- instance
            _       <- subject.put(keyDate, expects)
            fetched <- subject.get(keyDate)
          } yield fetched
        ).unsafeRunSync()

        assert(results == expects)
      }

      test("get returns value for closest previous date") {
        val expects = entity
        val results = (
          for {
            subject <- instance
            _       <- subject.put(ZonedDateTime.now().plusDays(1), expects.copy(energy = 6))
            _       <- subject.put(ZonedDateTime.now().plusDays(2), expects)
            _       <- subject.put(ZonedDateTime.now().plusDays(5), expects.copy(energy = 12))
            fetched <- subject.get(ZonedDateTime.now().plusDays(4))
          } yield fetched
        ).unsafeRunSync()

        assert(results == expects)
      }
    }

    test("ChargeSessionStorage") {
      def instance = ChargeSessionStorage.inMemory[IO]

      val entity =
        ChargeSessionStorage.Entity(
            ZonedDateTime.now().minusHours(2)
          , ZonedDateTime.now().minusMinutes(10)
          , 100
          , 20
          , 2
        )

      test("put should persist value") {
        val uniqKey = "test-id"
        val expects = entity
        val results = (
          for {
            subject <- instance
            _       <- subject.put(uniqKey, expects)
            fetched <- subject.get(uniqKey)
          } yield fetched
          ).unsafeRunSync()

        assert(results == List(expects))
      }

      test("get should return accumulated values ordered by date") {
        val newOne = entity
        val oldOne = newOne.copy(
            startedAt = newOne.startedAt.minusDays(1)
          , endedAt   = newOne.endedAt.minusDays(1)
        )

        val uniqKey = "test-id"
        val expects = List(newOne, oldOne)
        val results = (
          for {
            subject <- instance
            _       <- subject.put(uniqKey, oldOne)
            _       <- subject.put(uniqKey, newOne)
            fetched <- subject.get(uniqKey)
          } yield fetched
        ).unsafeRunSync()

        assert(results == expects)
      }
    }
  }
}
