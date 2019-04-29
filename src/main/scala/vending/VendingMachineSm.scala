package vending

import java.time.LocalDate

import cats.data.State
import cats.syntax.option._
import vending.Domain._

object VendingMachineSm {

  //    State monad is function
  // ╭----------------------------╮
  // |  State => (State, Effect)  |
  // ╰----------------------------╯
  //
  // In our case
  // Vending machine state => (New vending machine state, effects)

  def compose(action: Action, now: LocalDate): State[VendingMachineState, ActionResult] =
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
      expiredResult <- checkExpiryDate(action, now)
      //  result ⬅  application()
      //              ⬇ modified state
      maybeMbaf <- detectMoneyBoxAlmostFull()
      //  result ⬅  application()
      //              ⬇ modified state
      maybeDisplay <- maybeDisplayState(action)
    } yield ActionResult(
      userOutputs = List(updateResult, selectResult).flatten,
      systemReports = List(expiredResult, maybeMbaf).flatten ++ maybeShortage,
      displayState = maybeDisplay
    )

  def updateCredit(action: Action): State[VendingMachineState, Option[UserOutput]] =
    State[VendingMachineState, Option[UserOutput]] { s =>
      action match {
        case Credit(value) => (s.copy(credit = s.credit + value), CreditInfo(s.credit + value).some)
        case Withdrawn => (s.copy(credit = 0), CollectYourMoney.some)
        case _ => (s, none[UserOutput])
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

  def detectShortage(): State[VendingMachineState, List[ProductShortage]] = {
    State[VendingMachineState, List[ProductShortage]] { s =>
      val toNotify: Set[Product] = s.quantity.filter(_._2 == 0).keySet -- s.reportedShortage
      if (toNotify.isEmpty) {
        (s, List.empty[ProductShortage])
      } else {
        (s.copy(reportedShortage = s.reportedShortage ++ toNotify), toNotify.toList.map(ProductShortage))
      }
    }
  }

  def checkExpiryDate(action: Action, now: LocalDate): State[VendingMachineState, Option[SystemReporting]] =

    State[VendingMachineState, Option[SystemReporting]] { s =>
      if (action == CheckExpiryDate) {
        val products = s.quantity.keys.filter { p =>
          !p.expiryDate.isAfter(now) && !s.reportedExpiryDate.contains(p)
        }.toList
        val newState = s.copy(reportedExpiryDate = s.reportedExpiryDate ++ products)
        val result = if (products.nonEmpty) ExpiredProducts(products).some else none[SystemReporting]
        (newState, result)
      } else {
        (s, none[SystemReporting])
      }
    }

  def detectMoneyBoxAlmostFull(): State[VendingMachineState, Option[MoneyBoxAlmostFull]] = {
    State[VendingMachineState, Option[MoneyBoxAlmostFull]] { s =>
      if (!s.reportedMoneyBoxAlmostFull && s.income > 10) {
        (s.copy(reportedMoneyBoxAlmostFull = true), MoneyBoxAlmostFull(s.income).some)
      } else {
        (s, none[MoneyBoxAlmostFull])
      }
    }
  }

  def maybeDisplayState(action: Action): State[VendingMachineState, Option[Display]] =
    State[VendingMachineState, Option[Display]] { s =>
      val r = action match {
        case Credit(_) | Withdrawn | SelectProduct(_) => Display(s).some
        case _ => None
      }
      (s, r)
    }

  case class VendingMachineState(credit: Int,
                                 income: Int,
                                 quantity: Map[Product, Int] = Map.empty,
                                 reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product],
                                 reportedShortage: Set[Domain.Product] = Set.empty[Domain.Product],
                                 reportedMoneyBoxAlmostFull:Boolean = false
                                )

}
