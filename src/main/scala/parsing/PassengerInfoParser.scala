package parsing

import spray.json.DefaultJsonProtocol

object PassengerInfoParser {

  case class PassengerInfo(DocumentIssuingCountryCode: String, DateOfBirth: Option[String])
  case class FlightPassengerInfoResponse(ArrivalPortCode: String,
                                         VoyageNumber: String,
                                         CarrierCode: String,
                                         ScheduledDateOfArrival: String,
                                         PassengerList: List[PassengerInfo]){
    def flightCode: String = CarrierCode + VoyageNumber
  }

  object FlightPassengerInfoProtocol extends DefaultJsonProtocol {
    implicit val passengerInfoConverter = jsonFormat2(PassengerInfo)
    implicit val flightPassengerInfoResponseConverter = jsonFormat5(FlightPassengerInfoResponse)
  }

}
