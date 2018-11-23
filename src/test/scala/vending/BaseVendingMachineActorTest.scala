package vending

import akka.actor.{ActorRef, Props}

class BaseVendingMachineActorTest extends BaseActorTest {
  override def createActor(productsDef: List[Domain.Product],
                           quantity: Map[String, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new BaseVendingMachineActor(productsDef, quantity, userOutputActor, reportsActor)))
}
