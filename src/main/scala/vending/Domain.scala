package vending

import java.time.LocalDate

import cats.kernel.Monoid

object Domain {

  case object GetState

  sealed trait Action
  case class Credit(value: Int) extends Action
  case object Withdrawn extends Action
  case object CheckExpiryDate extends Action
  case class SelectProduct(number: String) extends Action
  case object Quit extends Action

  sealed trait UserOutput
  case object CollectYourMoney extends UserOutput
  case class CreditInfo(value: Int) extends UserOutput
  case object WrongProduct extends UserOutput
  case class NotEnoughOfCredit(diff: Int) extends UserOutput
  case class OutOfStock(product: Product) extends UserOutput
  case class GiveProductAndChange(selected: Product, change: Int) extends UserOutput
  case class Display(string: String) extends UserOutput

  sealed trait SystemReporting
  case class MoneyBoxAlmostFull(amount: Int) extends SystemReporting
  case class ProductShortage(products: Product) extends SystemReporting
  case class ExpiredProducts(products: List[Product]) extends SystemReporting

  case class ActionResult(userOutputs: List[UserOutput] = List.empty,
                          systemReports: List[SystemReporting] = List.empty) {
    def nonEmpty(): Boolean = userOutputs.nonEmpty || systemReports.nonEmpty
  }

  object ActionResult {

    implicit val monoid: Monoid[ActionResult] = new Monoid[ActionResult] {
      override def empty: ActionResult = ActionResult()
      override def combine(x: ActionResult, y: ActionResult): ActionResult =
        ActionResult(x.userOutputs ::: y.userOutputs, x.systemReports ::: y.systemReports)
    }
  }

  case class Product(price: Int, code: String, symbol: String, expiryDate: LocalDate)
}
