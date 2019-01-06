package vending

import java.time.LocalDate

import org.scalatest.{Matchers, WordSpec}
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState
import cats.syntax.option._

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
  val now: LocalDate = LocalDate.of(2018, 10, 1)

  "Vending machine" should {

    "successfully buy and give change" in {
      val (state, results) = (for {
        _ <- VendingMachineSm.buildMonad(Credit(10),now)
        r <- VendingMachineSm.buildMonad(SelectProduct("1"),now)
      } yield r).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(4)
      results.userOutputs.contains(GiveProductAndChange(beer, 7)) shouldBe true
    }

    "refuse to buy if not enough of money" in {
      val (state, results) = (for {
        _ <- VendingMachineSm.buildMonad(Credit(1),now)
        r <- VendingMachineSm.buildMonad(SelectProduct("1"),now)
      } yield r).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      results.userOutputs.contains(NotEnoughOfCredit(2)) shouldBe true
    }

    "refuse to buy for wrong product selection" in {
      val (state, results) = (for {
        _ <- VendingMachineSm.buildMonad(Credit(1),now)
        r <- VendingMachineSm.buildMonad(SelectProduct("3"),now)
      } yield r).run(vendingMachineState).value

      state.quantity.get(beer) shouldBe Some(5)
      state.credit shouldBe 1
      results.userOutputs.contains(WrongProduct) shouldBe true
    }

    "refuse to buy if out of stock" in {
      val (state, results) = (for {
        _ <- VendingMachineSm.buildMonad(Credit(10),now)
        r <- VendingMachineSm.buildMonad(SelectProduct("1"),now)
      } yield r).run(vendingMachineState.copy(quantity = Map(beer -> 0))).value

      state.quantity.get(beer) shouldBe Some(0)
      state.credit shouldBe 10
      results.userOutputs.contains(OutOfStock(beer)) shouldBe true
    }

    "track income" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.buildMonad(Credit(10),now)
          _ <- VendingMachineSm.buildMonad(SelectProduct("1"),now)
          _ <- VendingMachineSm.buildMonad(Credit(10),now)
          _ <- VendingMachineSm.buildMonad(SelectProduct("1"),now)
        } yield ()
        ).run(vendingMachineState).value

      state.income shouldBe 6
    }

    "track credit" in {
      val (state, (result0, result1)) = (
        for {
          r1 <- VendingMachineSm.buildMonad(Credit(10),now)
          r2 <- VendingMachineSm.buildMonad(Credit(1),now)
        } yield (r1, r2)).run(vendingMachineState).value

      result0.userOutputs.head shouldBe CreditInfo(10)
      result1.userOutputs.head shouldBe CreditInfo(11)
      state.credit shouldBe 11
    }

    "give back all money if withdraw" in {
      val (state, _) = (
        for {
          _ <- VendingMachineSm.buildMonad(Credit(10),now)
          _ <- VendingMachineSm.buildMonad(Withdrawn,now)
        } yield ()).run(vendingMachineState).value

      state.credit shouldBe 0
    }

    "report if money box is almost full" in {
      val (_, result) = (
        for {
          _ <- VendingMachineSm.buildMonad(Credit(200),now)
          r <- VendingMachineSm.buildMonad(SelectProduct("2"),now)
        } yield r).run(vendingMachineState).value

      result.systemReports.contains(MoneyBoxAlmostFull(100)) shouldBe true
    }

    "report shortage of product" in {
      val (_, result) = (
        for {
          _ <- VendingMachineSm.buildMonad(Credit(200),now)
          r <- VendingMachineSm.buildMonad(SelectProduct("2"),now)
        } yield r).run(vendingMachineState).value

      result.systemReports.contains(NotifyAboutShortage(pizza)) shouldBe true
    }

    "report issues with expiry date" in {
      val now = LocalDate.of(2019, 1, 1)
      val (state, (result1, result2)) = (
        for {
          r1 <- VendingMachineSm.buildMonad(CheckExpiryDate, now)
          r2 <- VendingMachineSm.buildMonad(CheckExpiryDate,now)
        } yield (r1, r2)).run(vendingMachineState).value

      result1.systemReports.contains(ExpiredProducts(List(pizza))) shouldBe true
      result2.systemReports.contains(ExpiredProducts(List(pizza))) shouldBe false
      state.reportedExpiryDate should contain(pizza)
    }

  }

  "expiry date monad" should {
    val exipiered = pizza.expiryDate.plusDays(1)
    val state0 = vendingMachineState.copy(

    )

    "find expired products" in {
      val (state1, results) = VendingMachineSm.checkExpiryDate(CheckExpiryDate,exipiered).run(state0).value

      results shouldBe ExpiredProducts(List(pizza)).some
      state1.reportedExpiryDate shouldBe Set(pizza)
    }

    "ignore expired products if already reported" in {
      val (state1, results) = (for {
        _ <- VendingMachineSm.checkExpiryDate(CheckExpiryDate,exipiered)
        r <- VendingMachineSm.checkExpiryDate(CheckExpiryDate,exipiered)
      } yield r).run(state0).value

      results shouldBe none[ExpiredProducts]
      state1.reportedExpiryDate shouldBe Set(pizza)
    }

    "check that all products are ok" in {
      val (state1, results) = VendingMachineSm.checkExpiryDate(CheckExpiryDate,LocalDate.MIN).run(state0).value

      results shouldBe none[ExpiredProducts]
      state1.reportedExpiryDate shouldBe Set.empty[Product]
    }
  }

  "update credit monad" should {

    "update credit when insert" in {

      val (state, (result1, result2)) = (for {
        r1 <- VendingMachineSm.updateCredit(Credit(2))
        r2 <- VendingMachineSm.updateCredit(Credit(5))
      } yield (r1, r2)).run(vendingMachineState).value

      state.credit shouldBe 7
      result1 shouldBe CreditInfo(2).some
      result2 shouldBe CreditInfo(7).some
    }

    "clear credit when withdrawn is selected" in {
      val state0 = vendingMachineState.copy(credit = 20)

      val (state1, result1) = VendingMachineSm.updateCredit(Withdrawn).run(state0).value

      state1.credit shouldBe 0
      result1 shouldBe CollectYourMoney.some
    }

  }

  "select product monad" should {

    val state0 = vendingMachineState.copy(
      credit = 10
    )
    "successfully buy product" in {
      val (state1, results) = VendingMachineSm.selectProduct(SelectProduct(beer.code)).run(state0).value

      state1.income shouldBe beer.price
      results shouldBe GiveProductAndChange(beer, 7).some
    }
    "refuse to buy if not enough of money" in {
      val (state1, results) = VendingMachineSm.selectProduct(SelectProduct(pizza.code)).run(state0).value

      state1.income shouldBe 0
      state1.credit shouldBe 10
      results shouldBe NotEnoughOfCredit(90).some
    }
  }

  "detect shortage monad" should {

    val state0 = vendingMachineState.copy(quantity = Map(beer -> 0))

    "detect shortage" in {
      val (_, results) = VendingMachineSm.detectShortage().run(state0).value
      results shouldBe List(NotifyAboutShortage(beer))
    }

    "ignore shortage for a second time" in {
      val (state, results) = (for {
        r1 <- VendingMachineSm.detectShortage()
        r2 <- VendingMachineSm.detectShortage()
      } yield (r1, r2)).run(state0).value

      results._1 shouldBe List(NotifyAboutShortage(beer))
      results._2 shouldBe List.empty
    }
  }

  "detect money box almost full monad" should {
    val state0 = vendingMachineState.copy(income = 100)
    "notify if money box is almost full" in {
      val (_, results) = VendingMachineSm.detectMoneyBoxAlmostFull().run(state0).value
      results shouldBe MoneyBoxAlmostFull(100).some
    }
  }

}
