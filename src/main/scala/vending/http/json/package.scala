package vending.http

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.util.Try

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import vending.Domain
import vending.Domain.Product
import vending.VendingMachineSm.VendingMachineState

package object json extends SprayJsonSupport with DefaultJsonProtocol {

  object Api extends SprayJsonSupport with DefaultJsonProtocol {

    sealed trait ResultV1

    case class CollectYourMoneyV1(messageType: String = "CollectYourMoneyV1") extends ResultV1
    case class CreditInfoV1(value: Int, messageType: String = "CreditInfoV1") extends ResultV1
    case class WrongProductV1(messageType: String = "WrongProductV1") extends ResultV1
    case class NotEnoughOfCreditV1(diff: Int, messageType: String = "NotEnoughOfCreditV1") extends ResultV1
    case class OutOfStockV1(code: String, messageType: String = "OutOfStockV1") extends ResultV1
    case class GiveProductAndChangeV1(code: String, change: Int, messageType: String = "GiveProductAndChangeV1") extends ResultV1

    case class ProductWithQuantityV1(code: String, symbol: String, price: Int, quantity: Int)
    case class VendingMachineStateV1(credit: Int,
                                     income: Int,
                                     quantity: List[ProductWithQuantityV1] = List.empty,
                                     reportedExpiryDate: Set[Domain.Product] = Set.empty[Domain.Product],
                                     reportedShortage: Set[Domain.Product] = Set.empty[Domain.Product]
                                    )
    case object VendingMachineStateV1 {
      def toApi(vm: VendingMachineState): VendingMachineStateV1 = {
        VendingMachineStateV1(
          credit = vm.credit,
          income = vm.income,
          quantity = vm.quantity.map(kv => ProductWithQuantityV1(kv._1.code,kv._1.symbol,kv._1.price,kv._2)).toList,
          reportedExpiryDate = vm.reportedExpiryDate,
          reportedShortage = vm.reportedShortage
        )
      }
    }
    case class DisplayV1(vendingMachineStateV1: VendingMachineStateV1, messageType: String = "DisplayV1") extends ResultV1
    case class MoneyBoxAlmostFullV1(amount: Int, messageType: String = "MoneyBoxAlmostFullV1") extends ResultV1
    case class ProductShortageV1(products: Product, messageType: String = "ProductShortageV1") extends ResultV1
    case class ExpiredProductsV1(products: List[Product], messageType: String = "ExpiredProductsV1") extends ResultV1

    implicit val collectYourMoneyFormat: RootJsonFormat[CollectYourMoneyV1] = jsonFormat1(CollectYourMoneyV1)
    implicit val creditInfoV1Format: RootJsonFormat[CreditInfoV1] = jsonFormat2(CreditInfoV1)
    implicit val wrongProductV1Format: RootJsonFormat[WrongProductV1] = jsonFormat1(WrongProductV1)
    implicit val notEnoughOfCreditV1Format: RootJsonFormat[NotEnoughOfCreditV1] = jsonFormat2(NotEnoughOfCreditV1)
    implicit val outOfStockV1Format: RootJsonFormat[OutOfStockV1] = jsonFormat2(OutOfStockV1)
    implicit val giveProductAndChangeV1Format: RootJsonFormat[GiveProductAndChangeV1] = jsonFormat3(GiveProductAndChangeV1)
    implicit val localTimeFormat: JsonFormat[LocalDate] = new JsonFormat[LocalDate] {
      private val formatter = DateTimeFormatter.ISO_DATE
      override def write(obj: LocalDate): JsValue = JsString(formatter.format(obj))

      override def read(json: JsValue): LocalDate = {
        json match {
          case JsString(s) =>
            Try(LocalDate.parse(s, formatter)).getOrElse(deserializationError("deserialization error"))
          case _ => deserializationError("deserialization error")
        }
      }
    }
    implicit val productFormat: RootJsonFormat[Product] = jsonFormat4(Product)
    //    implicit val vmStateFormat: RootJsonFormat[VendingMachineState] = jsonFormat5(VendingMachineState)
    implicit val productWithQuantityV1Format: RootJsonFormat[ProductWithQuantityV1] = jsonFormat4(ProductWithQuantityV1)
    implicit val vmStateFormat: RootJsonFormat[VendingMachineStateV1] = jsonFormat5(VendingMachineStateV1.apply)
    implicit val displayFormat: RootJsonFormat[DisplayV1] = jsonFormat2(DisplayV1)
    implicit val moneyBoxAlmostFullV1Format: RootJsonFormat[MoneyBoxAlmostFullV1] = jsonFormat2(MoneyBoxAlmostFullV1)
    implicit val productShortageV1Format: RootJsonFormat[ProductShortageV1] = jsonFormat2(ProductShortageV1)
    implicit val expiredProductsV1Format: RootJsonFormat[ExpiredProductsV1] = jsonFormat2(ExpiredProductsV1)

    implicit val resultV1Format: JsonFormat[ResultV1] = new JsonFormat[ResultV1] {
      override def read(json: JsValue): ResultV1 = {
        json.asJsObject.getFields("messageType").head match {
          case JsString("CollectYourMoneyV1") => collectYourMoneyFormat.read(json)
          case JsString("CreditInfoV1") => creditInfoV1Format.read(json)
          case JsString("WrongProductV1") => wrongProductV1Format.read(json)
          case JsString("NotEnoughOfCreditV1") => notEnoughOfCreditV1Format.read(json)
          case JsString("OutOfStockV1") => outOfStockV1Format.read(json)
          case JsString("GiveProductAndChangeV1") => giveProductAndChangeV1Format.read(json)
          case JsString("DisplayV1") => displayFormat.read(json)
          case JsString("MoneyBoxAlmostFullV1") => moneyBoxAlmostFullV1Format.read(json)
          case JsString("ProductShortageV1") => productShortageV1Format.read(json)
          case JsString("ExpiredProductsV1") => expiredProductsV1Format.read(json)
          case _ => deserializationError("Not supported message type")
        }
      }
      override def write(obj: ResultV1): JsValue = obj match {
        case collectYourMoneyV1: CollectYourMoneyV1 => collectYourMoneyV1.toJson
        case creditInfoV1: CreditInfoV1 => creditInfoV1.toJson
        case notEnoughOfCreditV1: NotEnoughOfCreditV1 => notEnoughOfCreditV1.toJson
        case outOfStockV1: OutOfStockV1 => outOfStockV1.toJson
        case giveProductAndChangeV1: GiveProductAndChangeV1 => giveProductAndChangeV1.toJson
        case wrongProductV1: WrongProductV1 => wrongProductV1.toJson
        case displayV1: DisplayV1 => displayV1.toJson
        case moneyBoxAlmostFullV1: MoneyBoxAlmostFullV1 => moneyBoxAlmostFullV1.toJson
        case productShortageV1: ProductShortageV1 => productShortageV1.toJson
        case expiredProductsV1: ExpiredProductsV1 => expiredProductsV1.toJson
      }
    }
  }

}
