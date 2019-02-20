package vending

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


  def updateCredit(action: Action): State[VendingMachineState, Option[UserOutput]] =
    State[VendingMachineState, Option[UserOutput]] { s =>
      action match {
        case Credit(value) => (s.copy(credit = s.credit + value), CreditInfo(s.credit + value).some)
        case Withdrawn => (s.copy(credit = 0), CollectYourMoney.some)
        case _ => (s, none[UserOutput])
      }
    }

  case class VendingMachineState(credit: Int,
                                 income: Int,
                                 quantity: Map[Product, Int] = Map.empty,
                                 reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product],
                                 reportedShortage: Set[Domain.Product] = Set.empty[Domain.Product]
                                )

}
