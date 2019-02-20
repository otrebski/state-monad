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

    "successfully buy and give change" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(10))
        e <- VendingMachineSm.compose(SelectProduct("1"))
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(4)
      effects.userOutputs.contains(GiveProductAndChange(beer, 7)) shouldBe true
    }

    "refuse to buy if not enough of money" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(1))
        e <- VendingMachineSm.compose(SelectProduct("1"))
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      effects.userOutputs.contains(NotEnoughOfCredit(2)) shouldBe true
    }

    "refuse to buy for wrong product selection" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(1))
        e <- VendingMachineSm.compose(SelectProduct("3"))
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      effects.userOutputs.contains(WrongProduct) shouldBe true
    }

    "refuse to buy if out of stock" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(10))
        e <- VendingMachineSm.compose(SelectProduct("1"))
      } yield e).run(vendingMachineState.copy(quantity = Map(beer -> 0))).value

      state.quantity.get(beer) shouldBe Some(0)
      state.credit shouldBe 10
      effects.userOutputs.contains(OutOfStock(beer)) shouldBe true
    }

    "track income" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.compose(Credit(10))
          _ <- VendingMachineSm.compose(SelectProduct("1"))
          _ <- VendingMachineSm.compose(Credit(10))
          _ <- VendingMachineSm.compose(SelectProduct("1"))
        } yield ()
        ).run(vendingMachineState).value

      state.income shouldBe 6
    }

    "track credit" in {
      val (state, (effects1, effects2)) = (
        for {
          e1 <- VendingMachineSm.compose(Credit(10))
          e2 <- VendingMachineSm.compose(Credit(1))
        } yield (e1, e2)).run(vendingMachineState).value

      effects1.userOutputs.head shouldBe CreditInfo(10)
      effects2.userOutputs.head shouldBe CreditInfo(11)
      state.credit shouldBe 11
    }

    "give back all money if withdraw" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.compose(Credit(10))
          _ <- VendingMachineSm.compose(Withdrawn)
        } yield ()).run(vendingMachineState).value

      state.credit shouldBe 0
    }

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

    val state0 = vendingMachineState.copy(
      credit = 10
    )
    "successfully buy product" in {
      val (state1, effects) = VendingMachineSm.selectProduct(SelectProduct(beer.code)).run(state0).value

      state1.income shouldBe beer.price
      effects shouldBe GiveProductAndChange(beer, 7).some
    }
    "refuse to buy if not enough of money" in {
      val (state1, effects) = VendingMachineSm.selectProduct(SelectProduct(pizza.code)).run(state0).value

      state1.income shouldBe 0
      state1.credit shouldBe 10
      effects shouldBe NotEnoughOfCredit(90).some
    }
  }

  "detect shortage monad" should {
    "detect shortage" in ???
    "ignore shortage for a second time" in ???
  }

  "detect money box almost full monad" should {
    "notify if money box is almost full" in ???
  }
}
