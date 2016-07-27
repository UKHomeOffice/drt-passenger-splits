package core.workload

import core.workload.PaxLoad.PaxType
import spray.http.DateTime

object PaxLoad {
  type PaxType = (String, Symbol)
}
case class PaxLoad(time: DateTime, nbPax: Int, paxType: PaxType) {

}
