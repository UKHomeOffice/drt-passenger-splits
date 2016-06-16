package parsing

import spray.json.DefaultJsonProtocol

object ChromaParser {

  case class ChromaToken(access_token: String, token_type: String, expires_in: Int)

  case class ChromaSingleFlight(Operator: String,
                                Status: String,
                                EstDT: String,
                                ActDT: String,
                                EstChoxDT: String,
                                ActChoxDT: String,
                                Gate: String,
                                Stand: String,
                                MaxPax: Int,
                                ActPax: Int,
                                TranPax: Int,
                                RunwayID: String,
                                BaggageReclaimId: String,
                                FlightID: Int,
                                AirportID: String,
                                Terminal: String,
                                ICAO: String,
                                IATA: String,
                                Origin: String,
                                SchDT: String)

  object ChromaParserProtocol extends DefaultJsonProtocol {
    implicit val chromaTokenFormat = jsonFormat3(ChromaToken)
    implicit val chromaSingleFlightFormat = jsonFormat20(ChromaSingleFlight)
  }

}
