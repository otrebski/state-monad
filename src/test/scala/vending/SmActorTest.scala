package vending

import akka.actor.{ActorRef, Props}
import cats.syntax.option._

class SmActorTest extends BaseActorTest {
  override def createActor(quantity: Map[Domain.Product, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef,
                           statePublisher: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new SmActor(quantity, userOutputActor.some, reportsActor, statePublisher)))
}
