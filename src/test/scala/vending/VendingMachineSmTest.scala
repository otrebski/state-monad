package vending

import java.time.LocalDate

import cats.syntax.option._
import org.scalatest.{Matchers, WordSpec}
import vending.Domain.{_}
import vending.VendingMachineSm.VendingMachineState

class VendingMachineSmTest extends WordSpec with Matchers {

  val now: LocalDate = LocalDate.of(2018, 10, 1)
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
        _ <- VendingMachineSm.compose(Credit(10), now)
        e <- VendingMachineSm.compose(SelectProduct("1"), now)
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(4)
      effects.userOutputs should contain(GiveProductAndChange(beer, 7))
    }

    "refuse to buy if not enough of money" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(1), now)
        e <- VendingMachineSm.compose(SelectProduct("1"), now)
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      effects.userOutputs should contain(NotEnoughOfCredit(2))
    }

    "refuse to buy for wrong product selection" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(1), now)
        e <- VendingMachineSm.compose(SelectProduct("3"), now)
      } yield e).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      effects.userOutputs should contain(WrongProduct)
    }

    "refuse to buy if out of stock" in {
      val (state, effects) = (for {
        _ <- VendingMachineSm.compose(Credit(10), now)
        e <- VendingMachineSm.compose(SelectProduct("1"), now)
      } yield e).run(vendingMachineState.copy(quantity = Map(beer -> 0))).value

      state.quantity.get(beer) shouldBe Some(0)
      state.credit shouldBe 10
      effects.userOutputs should contain(OutOfStock(beer))
    }

    "track income" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.compose(Credit(10), now)
          _ <- VendingMachineSm.compose(SelectProduct("1"), now)
          _ <- VendingMachineSm.compose(Credit(10), now)
          _ <- VendingMachineSm.compose(SelectProduct("1"), now)
        } yield ()
        ).run(vendingMachineState).value

      state.income shouldBe 6
    }

    "track credit" in {
      val (state, (effects1, effects2)) = (
        for {
          e1 <- VendingMachineSm.compose(Credit(10), now)
          e2 <- VendingMachineSm.compose(Credit(1), now)
        } yield (e1, e2)).run(vendingMachineState).value

      effects1.userOutputs.head shouldBe CreditInfo(10)
      effects2.userOutputs.head shouldBe CreditInfo(11)
      state.credit shouldBe 11
    }

    "give back all money if withdraw" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.compose(Credit(10), now)
          _ <- VendingMachineSm.compose(Withdrawn, now)
        } yield ()).run(vendingMachineState).value

      state.credit shouldBe 0
    }

    "report if money box is almost full" in {
      val (_, effects) = (
        for {
          _ <- VendingMachineSm.compose(Credit(200), now)
          e <- VendingMachineSm.compose(SelectProduct("2"), now)
        } yield e).run(vendingMachineState).value

      effects.systemReports should contain(MoneyBoxAlmostFull(100))
    }

    "detect shortage of product" in {
      val (_, effects) = (
        for {
          _ <- VendingMachineSm.compose(Credit(200), now)
          e <- VendingMachineSm.compose(SelectProduct("2"), now)
        } yield e).run(vendingMachineState).value

      effects.systemReports should contain(ProductShortage(pizza))
    }

    "report issues with expiry date" in {
      val now = LocalDate.of(2019, 1, 1)
      val (state, (effects1, effects2)) = (
        for {
          e1 <- VendingMachineSm.compose(CheckExpiryDate, now)
          e2 <- VendingMachineSm.compose(CheckExpiryDate, now)
        } yield (e1, e2)).run(vendingMachineState).value

      effects1.systemReports should contain(ExpiredProducts(List(pizza)))
      effects2.systemReports should not contain (ExpiredProducts(List(pizza)))
      state.reportedExpiryDate should contain(pizza)
    }

  }

  "expiry date monad" should {
    val expired = pizza.expiryDate.plusDays(1)
    val state0 = vendingMachineState.copy(

    )

    "find expired products" in {
      val (state1, effects) = VendingMachineSm.checkExpiryDate(CheckExpiryDate, expired).run(state0).value

      effects shouldBe ExpiredProducts(List(pizza)).some
      state1.reportedExpiryDate shouldBe Set(pizza)
    }

    "ignore expired products if already reported" in {
      val (state1, effects) = (for {
        _ <- VendingMachineSm.checkExpiryDate(CheckExpiryDate, expired)
        e <- VendingMachineSm.checkExpiryDate(CheckExpiryDate, expired)
      } yield e).run(state0).value

      effects shouldBe none[ExpiredProducts]
      state1.reportedExpiryDate shouldBe Set(pizza)
    }

    "check that all products are ok" in {
      val (state1, effects) = VendingMachineSm.checkExpiryDate(CheckExpiryDate, LocalDate.MIN).run(state0).value

      effects shouldBe none[ExpiredProducts]
      state1.reportedExpiryDate shouldBe Set.empty[Product]
    }
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

    val state0 = vendingMachineState.copy(quantity = Map(beer -> 0))

    "detect shortage" in {
      val (_, effects) = VendingMachineSm.detectShortage().run(state0).value
      effects shouldBe List(ProductShortage(beer))
    }

    "ignore shortage for a second time" in {
      val (_, effects) = (for {
        e1 <- VendingMachineSm.detectShortage()
        e2 <- VendingMachineSm.detectShortage()
      } yield (e1, e2)).run(state0).value

      effects._1 shouldBe List(ProductShortage(beer))
      effects._2 shouldBe List.empty
    }
  }

  "detect money box almost full monad" should {
    val state0 = vendingMachineState.copy(income = 100)
    "notify if money box is almost full" in {
      val (_, effects) = VendingMachineSm.detectMoneyBoxAlmostFull().run(state0).value
      effects shouldBe MoneyBoxAlmostFull(100).some
    }
  }

}
