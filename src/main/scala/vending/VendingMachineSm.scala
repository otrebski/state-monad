package vending

import java.time.LocalDate

import cats.Eval
import cats.data.{IndexedStateT, State}
import vending.Domain._
import cats.syntax.option._
import cats.syntax.monoid._
import cats.instances.list._

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
      maybeShortage <- detectShortage()
      //  result ⬅  application()
      //              ⬇ modified state
      expiredResult <- checkExpiryDate(action)
      //  result ⬅  application()
      //              ⬇ modified state
      maybeMbaf <- detectMoneyBoxAlmostFull()
    } yield ActionResult(
      userOutputs = List(updateResult, selectResult).flatten,
      systemReports = List(expiredResult, maybeMbaf).flatten |+| maybeShortage
    )

  def checkExpiryDate(action: Action): State[VendingMachineState, Option[SystemReporting]] =
    State[VendingMachineState, Option[SystemReporting]] { s =>
      if (action == CheckExpiryDate) {
        val products = s.quantity.keys.filter { p =>
          !p.expiryDate.isAfter(s.now) && !s.reportedExpiryDate.contains(p)
        }.toList
        val newState = s.copy(reportedExpiryDate = s.reportedExpiryDate ++ products)
        val result = if (products.nonEmpty) ExpiredProducts(products).some else none[SystemReporting]
        (newState, result)
      } else {
        (s, none[SystemReporting])
      }
    }

  def updateCredit(action: Action): State[VendingMachineState, Option[UserOutput]] =
    State[VendingMachineState, Option[UserOutput]] { s =>
      action match {
        case Credit(value) => (s.copy(credit = s.credit + value), CreditInfo(s.credit + value).some)
        case Withdrawn => (s.copy(credit = 0), CollectYourMoney.some)
        case _ => (s, none[UserOutput])
      }
    }

  def detectShortage(): State[VendingMachineState, List[NotifyAboutShortage]] = {
    State[VendingMachineState, List[NotifyAboutShortage]] { s =>
      val toNotify: Set[Product] = s.quantity.filter(_._2 == 0).keySet -- s.reportedShortage
      if (toNotify.isEmpty) {
        (s, List.empty[NotifyAboutShortage])
      } else {
        (s, toNotify.toList.map(NotifyAboutShortage))
      }
    }
  }

  def detectMoneyBoxAlmostFull(): State[VendingMachineState, Option[MoneyBoxAlmostFull]] = {
    State[VendingMachineState, Option[MoneyBoxAlmostFull]] { s =>
      if (s.income > 10) {
        (s, MoneyBoxAlmostFull(s.income).some)
      } else {
        (s, none[MoneyBoxAlmostFull])
      }
    }
  }

  def selectProduct(action: Action): State[VendingMachineState, Option[UserOutput]] =
    State[VendingMachineState, Option[UserOutput]] { s =>
      action match {
        case SelectProduct(number) =>
          val selected = number.toString
          val maybeProduct = s.quantity.keys.find(_.code == selected)
          val maybeQuantity = maybeProduct.map(s.quantity)
          (maybeProduct, maybeQuantity) match {
            case (Some(product), Some(quantity)) if product.price <= s.credit && quantity > 0 =>
              val giveChange = s.credit - product.price
              val newQuantity = quantity - 1
              val newState = s.copy(
                credit = 0,
                income = s.income + product.price,
                quantity = s.quantity.updated(product, newQuantity)
              )
              (newState, GiveProductAndChange(product, giveChange).some)

            case (Some(product), Some(q)) if q < 1 =>
              (s, OutOfStock(product).some)

            case (Some(product), _) =>
              (s, NotEnoughOfCredit(product.price - s.credit).some)

            case (None, _) =>
              (s, WrongProduct.some)
          }

        case _ => (s, none[UserOutput])
      }
    }

  case class VendingMachineState(credit: Int,
                                 income: Int,
                                 quantity: Map[Product, Int] = Map.empty,
                                 now: LocalDate = LocalDate.now(),
                                 reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product],
                                 reportedShortage: Set[Domain.Product] = Set.empty[Domain.Product]
                                )

}
