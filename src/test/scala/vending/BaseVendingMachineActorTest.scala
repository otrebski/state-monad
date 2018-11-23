package vending

import akka.actor.{ActorRef, Props}

class BaseVendingMachineActorTest extends BaseActorTest {
  override def createActor(quantity: Map[Domain.Product, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new BaseVendingMachineActor(quantity, userOutputActor, reportsActor)))
}
