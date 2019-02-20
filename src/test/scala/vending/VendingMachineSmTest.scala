package vending

import java.time.LocalDate

import cats.syntax.option._
import org.scalatest.{Matchers, WordSpec}
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

class VendingMachineSmTest extends WordSpec with Matchers {

  private val beer = Product(3, "1", Symbols.beer, LocalDate.of(2020, 12, 10))
  private val pizza = Product(100, "2", Symbols.pizza, LocalDate.of(2018, 12, 10))

  var vendingMachineState = VendingMachineState(
    credit = 0, income = 0,
    quantity = Map(
      beer -> 5,
      pizza -> 1
    )
  )

  "Vending machine" should {
    "successfully buy and give change" in ???
    "refuse to buy if not enough of money" in ???
    "refuse to buy for wrong product selection" in ???
    "refuse to buy if out of stock" in ???
    "track income" in ???
    "track credit" in ???
    "give back all money if withdraw" in ???
    "report if money box is almost full" in ???
    "detect shortage of product" in ???
    "report issues with expiry date" in ???
  }

  "expiry date monad" should {
    "find expired products" in ???
    "ignore expired products if already reported" in ???
    "check that all products are ok" in ???
  }

  "update credit monad" should {

    "update credit when insert" in {

      val (state, (effects1, effects2)) = (for {
        e1 <- VendingMachineSm.updateCredit(Credit(2))
        e2 <- VendingMachineSm.updateCredit(Credit(5))
      } yield (e1, e2)).run(vendingMachineState).value

      state.credit shouldBe 7
      effects1 shouldBe CreditInfo(2).some
      effects2 shouldBe CreditInfo(7).some
    }

    "clear credit when withdrawn is selected" in {
      val state0 = vendingMachineState.copy(credit = 20)

      val (state1, effects) = VendingMachineSm.updateCredit(Withdrawn).run(state0).value

      state1.credit shouldBe 0
      effects shouldBe CollectYourMoney.some
    }

  }

  "select product monad" should {
    "successfully buy product" in ???
    "refuse to buy if not enough of money" in ???
  }

  "detect shortage monad" should {
    "detect shortage" in ???
    "ignore shortage for a second time" in ???
  }

  "detect money box almost full monad" should {
    "notify if money box is almost full" in ???
  }
}
