package core

object PassengerTypeCalculator {

  object CountryCodes {
    val Austria = "AUT"
    val Belgium = "BEL"
    val Bulgaria = "BGR"
    val Croatia = "HRV"
    val Cyprus = "CYP"
    val Czech = "CZE"
    val Denmark = "DNK"
    val Estonia = "EST"
    val Finland = "FIN"
    val France = "FRA"
    val Germany = "DEU"
    val Greece = "GRC"
    val Hungary = "HUN"
    val Ireland = "IRL"
    val Italy = "ITA"
    val Latvia = "LCA"
    val Lithuania = "LTU"
    val Luxembourg = "LUX"
    val Malta = "MLT"
    val Netherlands = "NLD"
    val Poland = "POL"
    val Portugal = "PRT"
    val Romania = "ROU"
    val Slovakia = "SVK"
    val Slovenia = "SVN"
    val Spain = "ESP"
    val Sweden = "SWI"
    val UK = "GBR"
  }

  val EEACountries = {
    import CountryCodes._
    Set(
      Austria, Belgium, Bulgaria, Croatia, Cyprus, Czech,
      Denmark, Estonia, Finland, France, Germany, Greece, Hungary, Ireland,
      Italy, Latvia, Lithuania, Luxembourg, Malta, Netherlands, Poland,
      Portugal, Romania, Slovakia, Slovenia, Spain, Sweden,
      UK
    )
  }

  object DocType {
    val Visa = "V"
    val Passport = "P"
  }

  val nonMachineReadableCountries = {
    import CountryCodes._
    Set(Italy, Greece, Slovakia, Portugal)
  }

  val EEA = "EEA"

  def paxType(eeaFlag: String, documentCountry: String, documentType: String) = {
    (eeaFlag, documentCountry, documentType) match {
      case (EEA, country, _) if nonMachineReadableCountries contains (country) => "eea-non-machine-readable"
      case (EEA, country, _) if (EEACountries contains country) => "eea-machine-readable"
      case ("", country, DocType.Visa)  if !(EEACountries contains country) => "national-visa"
      case ("", country, DocType.Passport) if !(EEACountries contains country) => "national-non-visa"
    }
  }
}
