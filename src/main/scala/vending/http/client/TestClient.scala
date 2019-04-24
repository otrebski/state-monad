package vending.http.client

import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import io.gatling.core.stats.writer.BufferedFileChannelWriter
import spray.json._
import vending.http.client.TestClient.Api.Endpoint
import vending.http.json.Api._

//https://github.com/nuxeo/gatling-report -> different gatling report processor

object TestClient extends App {

  val duration: FiniteDuration = 45 seconds

  def now() = System.currentTimeMillis()

  implicit val as: ActorSystem = ActorSystem("client")
  implicit val m: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = as.dispatcher

  private val simulationStart: Long = now()

  val stats = as.actorOf(Props(new StatsActor(BufferedFileChannelWriter("test"))))

  private val sessionsCount = 10
  private val countDownLatch = new CountDownLatch(sessionsCount)
  (0 until sessionsCount).foreach { id =>
    val endpoint = Endpoint(id, "sm", "192.168.0.231")
    as.scheduler.scheduleOnce((id * 3) seconds, () => startUser(duration, stats)(endpoint))
  }

  countDownLatch.await()
  stats ! FlushAndClose
  as.terminate()

  def startUser(duration: FiniteDuration, statsActor: ActorRef)(implicit endpoint: Api.Endpoint): Unit = {
    //    implicit val e = endpoint
    println(s"Starting load for user ${endpoint.userId} and actor type ${endpoint.actorType}")
    val future: Future[Source[ServerSentEvent, NotUsed]] = Api.sseEvents()
    var start = now()

    future.foreach { stream =>
      val userId = endpoint.userId.toString
      statsActor ! LogUserStart(userId, start, now())
      val random = new Random(now())

      stream
        .takeWithin(duration)
        .runForeach { sse =>
          val data = sse.data
          if (data.length > 0) {
            val v = Try(resultV1Format.read(data.parseJson))
            v match {
              case Success(r) =>
                r match {

                  case _: CreditInfoV1 =>
                    statsActor ! LogResponse(userId, "credit", start, now())
                    if (random.nextInt(40) < 1) {
                      Api.withdraw()
                    } else {
                      Api.selectProduct(random.nextInt(11) + 1)
                    }
                    start = now()

                  case _: GiveProductAndChangeV1 =>
                    statsActor ! LogResponse(userId, "buy", start, now())
                    Api.insertCoin(random.nextInt(30))
                    start = now()

                  case NotEnoughOfCreditV1(diff, _) =>
                    statsActor ! LogResponse(userId, "no enough money", start, now())
                    Api.insertCoin(diff)
                    start = now()

                  case _: CollectYourMoneyV1 =>
                    statsActor ! LogResponse(userId, "withdrawn", start, now())
                    Api.insertCoin(random.nextInt(30))
                    start = now()

                  case _: WrongProductV1 =>
                    statsActor ! LogResponse(userId, "wrong product", start, now())
                    Api.selectProduct(random.nextInt(10) + 1)
                    start = now()

                  case _ =>
                }

              case Failure(e) =>
                e.printStackTrace()
            }

          }
        }.onComplete {
        case Success(value) =>
          println(s"Stream for user ${endpoint.userId} is done with value $value")
          statsActor ! LogUserEnd(userId, start, now())
          countDownLatch.countDown()
        case Failure(exception) =>
          println(s"Stream is done with exception ${exception.getMessage}")
          countDownLatch.countDown()
      }

      future.onComplete {
        case Success(value) =>
          println(s"Have stream $value")
          Api.insertCoin(5)
        case Failure(e) => e.printStackTrace()
      }
    }

  }

  case class LogResponse(user: String, action: String, start: Long, end: Long)
  case class LogUserStart(user: String, start: Long, now: Long)
  case class LogUserEnd(user: String, start: Long, now: Long)
  case object FlushAndClose

  class StatsActor(writer: BufferedFileChannelWriter) extends Actor {

    override def preStart(): Unit = {
      writer.startSimulation("zssdfsfsfz", "ss", simulationStart)
    }
    override def receive: Receive = {
      case LogResponse(user, action, begin, now) =>
        writer.response(user, action, begin, now)
      case LogUserStart(user, begin, end) =>
        writer.startUser(user, "vm", begin, end)
      case LogUserEnd(user, begin, end) =>
        writer.endUser(user, "vm", begin, end)

      case FlushAndClose =>
        writer.flush()
        writer.close()
    }
  }

  object Api {
    case class Endpoint(userId: Int, actorType: String, host: String)

    def sseEvents()(implicit endpoint: Endpoint): Future[Source[ServerSentEvent, NotUsed]] = Http()
      .singleRequest(HttpRequest(uri = s"http://${endpoint.host}:8080/api/${endpoint.actorType}/${endpoint.userId}/events"))
      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])

    private def requestOn(url: String) = Http().singleRequest(HttpRequest(uri = url))

    def selectProduct(code: Int)(implicit endpoint: Endpoint): Future[HttpResponse] =
      requestOn(s"http://${endpoint.host}:8080/api/${endpoint.actorType}/${endpoint.userId}/select/$code")

    def withdraw()(implicit endpoint: Endpoint): Future[HttpResponse] =
      requestOn(s"http://${endpoint.host}:8080/api/${endpoint.actorType}/${endpoint.userId}/withdrawn")

    def insertCoin(amount: Int)(implicit endpoint: Endpoint): Future[HttpResponse] =
      requestOn(s"http://${endpoint.host}:8080/api/${endpoint.actorType}/${endpoint.userId}/credit/$amount")

  }
}
