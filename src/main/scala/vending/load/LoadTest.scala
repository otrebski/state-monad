package vending.load

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import cats.syntax.option._
import vending.Domain.{Credit, CreditInfo, GiveProductAndChange, NotEnoughOfCredit, Product, SelectProduct, WrongProduct}
import vending.{BaseVendingMachineActor, SmActor, Symbols}

object LoadTest {

  def main(args: Array[String]): Unit = {
    runLoadTests()
  }

  def runLoadTests(): Unit = {
    val smActorProps = new ((Map[vending.Domain.Product, Int], ActorRef) => Props) {
      override def apply(quantity: Map[Product, Int],
                         ref: ActorRef): Props = {
        Props(new SmActor(quantity, ref.some, ref, ref))
      }
    }
    val baseActorProps = new ((Map[vending.Domain.Product, Int], ActorRef) => Props) {
      override def apply(quantity: Map[Product, Int],
                         ref: ActorRef): Props = {
        Props(new BaseVendingMachineActor(quantity, ref.some, ref, ref))
      }
    }
    implicit val system: ActorSystem = ActorSystem("s")
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    val r = for {
      l1 <- run(smActorProps, 10 seconds, "SM warmaup ")
      l2 <- run(baseActorProps, 10 seconds, "Base Warmup")
      vm1 <- run(smActorProps, 60 seconds, "State Monad 1")
      vb1 <- run(baseActorProps, 60 seconds, "Base Implem 1")
      vm2 <- run(smActorProps, 60 seconds, "State Monad 2")
      vb2 <- run(baseActorProps, 60 seconds, "Base Implem 2")
    } yield l1 :: l2 :: vm1 :: vm2 :: vb1 :: vb2 :: Nil

    r.onComplete {
      case Success(list) =>
        list.foreach { result =>
          println(f"${result.name}:\t ${result.processed.count}%,d\t ${result.duration.toSeconds}s [${
            result.processed
              .count / result.duration.toSeconds
          } msg/sec]")
        }
        system.terminate()
      case Failure(exception) =>
        println(s"Can't run test: ${exception.getMessage}")
        system.terminate()
    }

  }

  private val expiryDate: LocalDate = LocalDate.now().plusDays(2)

  private val quantity: Map[Product, Int] = (0 to 10).map(
    i => Product(price = 5, code = s"$i", symbol = Symbols.candy, expiryDate = expiryDate) -> Integer.MAX_VALUE
  ).toMap

  def run(
           propsCreator: (Map[vending.Domain.Product, Int], ActorRef) => Props,
           duration: FiniteDuration,
           testName: String
         )
         (implicit system: ActorSystem): Future[TestResult] = {
    val actorCount = 400
    val promise = Promise[TestResult]
    val statsGather = system.actorOf(Props(new StatsGather(actorCount, testName, promise)))
    (0 until actorCount).map { i =>
      val mock = system.actorOf(Props(new OutputMocks(statsGather)))
      val ref = system.actorOf(propsCreator.apply(quantity, mock))
      ref ! Credit(15)
      system.scheduler.scheduleOnce(duration, ref, PoisonPill)(ExecutionContext.global)
      system.scheduler.scheduleOnce(duration, mock, PoisonPill)(ExecutionContext.global)
    }
    promise.future
  }

  case class Processed(count: Long)
  case class TestResult(name: String, processed: Processed, duration: FiniteDuration)

  class StatsGather(var actorsCount: Int, testName: String, promise: Promise[TestResult]) extends Actor {
    var processed: Long = 0
    val start: Long = System.currentTimeMillis()
    override def receive: Receive = {
      case Processed(count) =>
        processed += count
        actorsCount -= 1
        if (actorsCount == 0) {
          val duration = FiniteDuration(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
          promise.complete(Success(TestResult(testName, Processed(processed), duration)))
          println(s"Completed $testName: $processed")
          context.stop(self)
        }
    }
  }

  class OutputMocks(statsGather: ActorRef) extends Actor {

    private val random = new Random()
    private var sold = 0

    override def postStop(): Unit = {
      statsGather ! Processed(sold)
      super.postStop()
    }
    override def receive: Receive = {
      case _: CreditInfo | WrongProduct =>
        sender() ! SelectProduct(s"${random.nextInt(11) + 1}")
      case _: GiveProductAndChange =>
        sender() ! Credit(random.nextInt(100) + 1)
        sold = sold + 1
      case NotEnoughOfCredit(diff) =>
        sender() ! Credit(diff)
      case _: Any =>
    }
  }
}
