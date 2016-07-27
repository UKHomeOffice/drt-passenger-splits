package core.workload

import core.PassengerInfoRouterActor.VoyagePaxSplits
import core.PassengerQueueTypes
import core.PassengerQueueTypes.PaxTypeAndQueueCount
import core.workload.PaxLoad.PaxType
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Tables
import spray.http.DateTime

import scala.annotation.tailrec

object PaxLoadCalculator {
  val paxType = (PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk)

  def calculatePaxLoadByDesk(voyagePaxSplits: VoyagePaxSplits, flowRate: => Int): Map[Symbol, Seq[PaxLoad]] = {
    val firstMinute = voyagePaxSplits.scheduledArrivalDateTime
    val groupedByDesk: Map[Symbol, Seq[PaxTypeAndQueueCount]] = voyagePaxSplits.paxSplits.groupBy(_.queueType)
    groupedByDesk.mapValues(
      (paxTypeAndCount: Seq[PaxTypeAndQueueCount]) => {
        val totalPax = paxTypeAndCount.map(_.paxCount).sum
        val headPaxType = paxTypeAndCount.head
        calcPaxLoad(firstMinute, totalPax, (headPaxType.passengerType, headPaxType.queueType), flowRate).reverse
      }
    )
  }

  def calcPaxLoad(currMinute: DateTime, remaining: Int, paxType: PaxType, flowRate: Int): List[PaxLoad] = {
    if (remaining <= flowRate)
      PaxLoad(currMinute, remaining, paxType) :: Nil
    else
      calcPaxLoad(currMinute + 60000, remaining - flowRate, paxType, flowRate) :::
        PaxLoad(currMinute, flowRate, paxType) :: Nil
  }
}

class WorkloadCalculatorSpec extends Specification with Matchers with Tables {


  "Workload Calculator" >> {
    "Given a simple VoyagePaxSplitsLoad to a single eeaDesk" in {
      val voyagePaxSplits = VoyagePaxSplits("STN", "BA", "978", 100, DateTime(2015, 5, 12, 12, 45), Seq(
        PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 32)
      ))

      "and a flow rate of 10 / minutes" in {
        def flowRate = 10
        val paxLoad = PaxLoadCalculator.calculatePaxLoadByDesk(voyagePaxSplits, flowRate)

        val paxType = (PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk)

        val expectedPaxLoadByDesk: Map[Symbol, Seq[PaxLoad]] =
          Map(PassengerQueueTypes.Desks.eeaDesk ->
            (PaxLoad(DateTime(2015, 5, 12, 12, 45), 10, paxType) ::
              PaxLoad(DateTime(2015, 5, 12, 12, 46), 10, paxType) ::
              PaxLoad(DateTime(2015, 5, 12, 12, 47), 10, paxType) ::
              PaxLoad(DateTime(2015, 5, 12, 12, 48), 2, paxType) :: Nil)
          )
        paxLoad should beEqualTo(expectedPaxLoadByDesk)
      }
    }
    "Given another simple VoyagePaxSplitsLoad to a single eeaDesk" in {
      val voyagePaxSplits = VoyagePaxSplits("STN", "BA", "978", 100, DateTime(2015, 6, 10, 6, 0), Seq(
        PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 32)
      ))

      "and a flow rate of 10 / minutes" in {
        def flowRate = 10
        val paxLoad = PaxLoadCalculator.calculatePaxLoadByDesk(voyagePaxSplits, flowRate)

        val paxType = (PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk)

        val expectedPaxLoadByDesk: Map[Symbol, Seq[PaxLoad]] =
          Map(PassengerQueueTypes.Desks.eeaDesk ->
            (PaxLoad(DateTime(2015, 6, 10, 6, 0), 10, paxType) ::
              PaxLoad(DateTime(2015, 6, 10, 6, 1), 10, paxType) ::
              PaxLoad(DateTime(2015, 6, 10, 6, 2), 10, paxType) ::
              PaxLoad(DateTime(2015, 6, 10, 6, 3), 2, paxType) :: Nil)
          )
        paxLoad should beEqualTo(expectedPaxLoadByDesk)
      }
    }
    "VoyagePaxSplitsLoad affecting two desks should distribute the load to the appropriate desks" in {
      val voyagePaxSplits = VoyagePaxSplits("STN", "BA", "978", 100, DateTime(2015, 6, 10, 6, 0), Seq(
        PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 32),
        PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.nationalsDesk, 48)
      ))

      "and a flow rate of 10 / minutes" in {
        def flowRate = 10
        val paxLoad = PaxLoadCalculator.calculatePaxLoadByDesk(voyagePaxSplits, flowRate)

        val paxType = (PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk)
        val nationalsPaxType = (PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.nationalsDesk)

        val expectedPaxLoadByDesk: Map[Symbol, Seq[PaxLoad]] =
          Map(
            PassengerQueueTypes.Desks.nationalsDesk ->
              (PaxLoad(DateTime(2015, 6, 10, 6, 0), 10, nationalsPaxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 1), 10, nationalsPaxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 2), 10, nationalsPaxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 3), 10, nationalsPaxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 4), 8, nationalsPaxType) :: Nil),
            PassengerQueueTypes.Desks.eeaDesk ->
              (PaxLoad(DateTime(2015, 6, 10, 6, 0), 10, paxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 1), 10, paxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 2), 10, paxType) ::
                PaxLoad(DateTime(2015, 6, 10, 6, 3), 2, paxType) :: Nil)
          )
        paxLoad should beEqualTo(expectedPaxLoadByDesk)

      }
    }
  }
}
