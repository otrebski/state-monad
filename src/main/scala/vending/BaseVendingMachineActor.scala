package vending

import java.time.LocalDate

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef}
import cats.syntax.show._
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

class BaseVendingMachineActor(
                               var quantity: Map[Product, Int],
                               userReportActor: ActorRef,
                               reportsActor: ActorRef
                             ) extends Actor {

  var credit: Int = 0
  var income: Int = 0
  var expiryNotified: Set[Domain.Product] = Set.empty

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler
      .schedule(5 seconds, 5 seconds, self, CheckExpiryDate)(context.system.dispatcher)
  }

  override def receive: Receive = {
    case SelectProduct(number) =>
      val selected = number.toString
      val maybeProduct = quantity.keys.find(_.code == selected)
      val maybeQuantity = maybeProduct.map(quantity)
      (maybeProduct, maybeQuantity) match {
        case (Some(product), Some(q)) if product.price <= credit && q > 0 =>
          val giveChange = credit - product.price            // calculating new state
          val newQuantity = q - 1                            // .....................

          credit = 0                                         // Changing internal state
          income += product.price                            // .......................
          quantity = quantity.updated(product, newQuantity)  // ......................

          userReportActor ! GiveProductAndChange(product, giveChange)   //Execute side effects
          if (newQuantity == 0) reportsActor ! ProductShortage(product) //....................
          if (income > 10) reportsActor ! MoneyBoxAlmostFull(income)    //....................
          userReportActor ! Display(currentState())                     //....................

        case (Some(product), Some(q)) if q < 1 =>
          userReportActor ! OutOfStock(product)
          userReportActor ! Display(currentState())
        case (Some(product), _) =>
          userReportActor ! NotEnoughOfCredit(product.price - credit)
          userReportActor ! Display(currentState())

        case (None, _) =>
          userReportActor ! WrongProduct
          userReportActor ! Display(currentState())
      }

    case Credit(value) =>
      credit += value
      userReportActor ! CreditInfo(credit)
      userReportActor ! Display(currentState())

    case Withdrawn =>
      credit = 0
      userReportActor ! CollectYourMoney
      userReportActor ! Display(currentState())

    case CheckExpiryDate =>
      val now = LocalDate.now()
      val expiredProducts = quantity.keys.filter { p =>
        !p.expiryDate.isAfter(now) && !expiryNotified.contains(p)
      }
      expiryNotified = expiryNotified ++ expiredProducts.toSet
      if (expiredProducts.nonEmpty) {
        reportsActor ! ExpiredProducts(expiredProducts.toList)
      }

    case GetState =>

      val vm = VendingMachineState(
        credit, income,
        quantity,
        reportedExpiryDate = expiryNotified)
      sender() ! vm

  }

  def currentState(): String = {
    VendingMachineState(
      credit, income,
      quantity,
      reportedExpiryDate = expiryNotified).show

  }
}
