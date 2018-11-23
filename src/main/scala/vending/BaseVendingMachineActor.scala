package vending

import java.time.LocalDate

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef}
import cats.syntax.show._
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

class BaseVendingMachineActor(var quantity: Map[Product, Int],
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

    case Credit(value) =>
      credit += value
      userReportActor ! CreditInfo(credit)

    case Withdrawn =>
      credit = 0
      userReportActor ! CollectYourMoney

    case SelectProduct(number) =>
      val selected = number.toString
      val maybeProduct = quantity.keys.find(_.code == selected)
      val maybeQuantity = maybeProduct.map(quantity)
      (maybeProduct, maybeQuantity) match {
        case (Some(product), Some(q)) if product.price <= credit && q > 0 =>
          val giveChange = credit - product.price
          val newQuantity = q - 1

          credit = 0
          income += product.price
          quantity = quantity.updated(product, newQuantity)

          userReportActor ! GiveProductAndChange(product, giveChange)

          if (newQuantity == 0) reportsActor ! NotifyAboutShortage(product)
          if (income > 10) reportsActor ! MoneyBoxAlmostFull(income)

        case (Some(product), Some(q)) if q < 1 =>
          userReportActor ! OutOfStock(product)

        case (Some(product), _) =>
          userReportActor ! NotEnoughOfCredit(product.price - credit)

        case (None, _) =>
          userReportActor ! WrongProduct
      }

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

    case AskForStateAsString =>
      val vm = VendingMachineState(
        credit, income,
        quantity,
        reportedExpiryDate = expiryNotified)
      sender() ! vm.show

  }
}
