package com.tvi.api

import java.time.ZonedDateTime

import utest._
import Domain.ChargeSession
import Storage.TariffStorage
import Service._

object ServiceTests extends TestSuite {
  val tests = Tests {
    test("FeesCalculator") {
      val session = ChargeSession("test-id", ZonedDateTime.now().minusHours(2), ZonedDateTime.now(), 100)
      val tariff  = TariffStorage.Entity(0.2, None, 0.5)

      test("calculate should calculate fees without parking fee") {
        val results = FeesCalculator.default.calculate(session, tariff)
        assert(results == (20, 30))
      }

      test("calculate should calculate fees with parking fee") {
        val results = FeesCalculator.default.calculate(session, tariff.copy(parking = Some(10)))
        assert(results == (40, 60))
      }

      test("calculate should round results") {
        val results = FeesCalculator.default.calculate(session, tariff.copy(energy = 0.00199999))
        assert(results == (0.2, 0.3))
      }
    }
  }
}
