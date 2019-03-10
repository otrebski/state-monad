import cats.Show
import vending.Domain.{ActionResult, SystemReporting, UserOutput}
import vending.VendingMachineSm.VendingMachineState

package object vending {
  implicit val vendingShow: Show[VendingMachineState] = (t: VendingMachineState) => {
    val products = t.quantity.map { case (product, quantity) =>
      s"${product.code}: ${product.price}PLN <${product.symbol * quantity}>"
    }
    s"""
       | +-----------------------------+
       | |     Vending Machine 2.0     |
       | +-----------------------------+
       | | Credit: ${t.credit}PLN
       | | Products:
         ${products.map(x => s"| | $x").mkString("\n")}
       | +-----------------------------+
       | | Maintenance info:
       | | Income: ${t.income}PLN
       | +-----------------------------+
       |"""
  }.stripMargin

  implicit class ListOfUserOutputsOps(list: List[UserOutput]) {
    def actionResult(): ActionResult = ActionResult(userOutputs = list)
  }

  implicit class ListOfSystemReportsOps(list: List[SystemReporting]) {
    def actionResult(): ActionResult = ActionResult(systemReports = list)
  }

  implicit class SystemReportOps(systemReporting: SystemReporting) {
    def actionResult(): ActionResult = ActionResult(systemReports = List(systemReporting))
  }

  implicit class UserOutputsOps(userOutput: UserOutput) {
    def actionResult(): ActionResult = ActionResult(userOutputs = List(userOutput))
  }

  implicit class StringOps(s: String) {
    def inBox(): String = {
      val maxLength = s.lines.map(_.toCharArray.length).max
      s.lines.map { line =>
        s"""│$line${" " * (maxLength - line.length)}│"""
      }.mkString(s"╭${"─" * maxLength}╮\n", "\n", s"\n╰${"─" * maxLength}╯")
    }
  }

}
