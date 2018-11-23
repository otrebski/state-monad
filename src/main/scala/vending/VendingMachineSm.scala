package vending

import java.time.LocalDate

import cats.Eval
import cats.data.{IndexedStateT, State}
import cats.syntax.monoid._
import vending.Domain._

object VendingMachineSm {
  def process(action: Action, vendingMachineState: VendingMachineState): (VendingMachineState, ActionResult) = {
    process(action).run(vendingMachineState).value
  }
  def process(action: Action): IndexedStateT[Eval, VendingMachineState, VendingMachineState, ActionResult] =
    for {
      updateResult <- updateCredit(action)
      //  result ⬅  application()
      //              ⬇ modified state
      selectResult <- selectProduct(action)
      //  result ⬅  application()
      //              ⬇ modified state
      expiredResult <- checkExpiryDate(action)
    } yield updateResult |+| expiredResult |+| selectResult
  def checkExpiryDate(action: Action): State[VendingMachineState, ActionResult] =
    State[VendingMachineState, ActionResult] { s =>
      if (action == CheckExpiryDate) {
        val products = s.quantity.keys.filter { p =>
          !p.expiryDate.isAfter(s.now) && !s.reportedExpiryDate.contains(p)
        }.toList
        val newState = s.copy(reportedExpiryDate = s.reportedExpiryDate ++ products)
        val result = if (products.nonEmpty) ExpiredProducts(products).actionResult() else ActionResult()
        (newState, result)
      } else {
        (s, ActionResult())
      }
    }
  def updateCredit(action: Action): State[VendingMachineState, ActionResult] =
    State[VendingMachineState, ActionResult] { s =>
      action match {
        case Credit(value) => (s.copy(credit = s.credit + value), CreditInfo(s.credit + value).actionResult())
        case Withdrawn => (s.copy(credit = 0), CollectYourMoney.actionResult())
        case _ => (s, ActionResult())
      }
    }
  def selectProduct(action: Action): State[VendingMachineState, ActionResult] =
    State[VendingMachineState, ActionResult] { s =>
      action match {
        case SelectProduct(number) =>
          val selected = number.toString

          val maybeProduct = s.quantity.keys.find(_.code == selected)

          val maybeQuantity = maybeProduct.map(s.quantity)
          (maybeProduct, maybeQuantity) match {
            case (Some(product), Some(q)) if product.price <= s.credit && q > 0 =>
              val giveChange = s.credit - product.price
              val newQuantity = q - 1
              val newState = s.copy(
                credit = 0,
                income = s.income + product.price,
                quantity = s.quantity.updated(product, newQuantity)
              )
              val list = GiveProductAndChange(product, giveChange).actionResult()
              val shortage =
                if (newQuantity == 0) NotifyAboutShortage(product).actionResult() else ActionResult()

              val moneyBoxAlmostFull =
                if (newState.income > 10) MoneyBoxAlmostFull(newState.income).actionResult() else ActionResult()

              val result = list |+| shortage |+| moneyBoxAlmostFull
              (newState, result)

            case (Some(product), Some(q)) if q < 1 =>
              (s, OutOfStock(product).actionResult())

            case (Some(product), _) =>
              (s, NotEnoughOfCredit(product.price - s.credit).actionResult())

            case (None, _) =>
              (s, WrongProduct.actionResult())
          }

        case _ => (s, ActionResult())
      }
    }
  case class VendingMachineState(credit: Int,
                                 income: Int,
                                 quantity: Map[Product, Int] = Map.empty,
                                 now: LocalDate = LocalDate.now(),
                                 reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product]
                                )

}
