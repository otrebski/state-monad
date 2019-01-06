package vending

import akka.actor.Actor
import vending.Domain._

class SystemReportsActor extends Actor {
  override def receive: Receive = {
    case ProductShortage(product) =>
      println(s" <To owner> We are short of ${product.symbol}".inBox())
    case MoneyBoxAlmostFull(amount) =>
      println(s" <To owner> We have ${amount}PLN in money box! Come and collect your profit!".inBox())
    case ExpiredProducts(products) =>
      println(s" <To owner> Following products will soon expire: ${products.map(_.symbol)}".inBox())
  }

}
