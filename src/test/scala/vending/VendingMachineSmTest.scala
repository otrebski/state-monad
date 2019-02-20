package vending

import org.scalatest.{Matchers, WordSpec}

class VendingMachineSmTest extends WordSpec with Matchers {

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
    "update credit when insert" in ???
    "clear credit when withdrawn is selected" in ???
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
