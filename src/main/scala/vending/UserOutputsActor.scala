package vending

import akka.actor.Actor
import vending.Domain._

class UserOutputsActor extends Actor {
  override def receive: Receive = {
    case CreditInfo(value) => println(s" <To User> Your credit is $value".inBox())
    case CollectYourMoney => println(" <To User> Collect your money".inBox())
    case WrongProduct => println(" <To User> Wrong product, select again".inBox())
    case NotEnoughOfCredit(diff) => println(s" <To User> Not enough of credit, insert $diff".inBox())
    case OutOfStock(product) => println(s" <To User> We are out of stock of ${product.symbol}, select different one".inBox())
    case GiveProductAndChange(product, change) =>
      println(s" <To User> Here is your ${product.symbol} and ${change}PLN of change".inBox())
    case Display(s) => println(s)
  }

}
