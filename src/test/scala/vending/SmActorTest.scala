package vending

import akka.actor.{ActorRef, Props}

class SmActorTest extends BaseActorTest {
  override def createActor(quantity: Map[Domain.Product, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new SmActor(quantity, userOutputActor, reportsActor)))
}
