package vending

import cats.data.State
import cats.syntax.option._
import vending.Domain._
import cats.syntax.show._

object VendingMachineSm {

  //    State monad is function
  // ╭----------------------------╮
  // |  State => (State, Effect)  |
  // ╰----------------------------╯
  //
  // In our case
  // Vending machine state => (New vending machine state, effects)


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

  def maybeDisplayState(action: Action): State[VendingMachineState, Option[Display]] =
    State[VendingMachineState, Option[Display]] { s =>
      val r = action match {
        case Credit(_) | Withdrawn | SelectProduct(_) => Display(s.show).some
        case _ => None
      }
      (s, r)
    }

  case class VendingMachineState(credit: Int,
                                 income: Int,
                                 quantity: Map[Product, Int] = Map.empty,
                                 reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product],
                                 reportedShortage: Set[Domain.Product] = Set.empty[Domain.Product]
                                )

}
