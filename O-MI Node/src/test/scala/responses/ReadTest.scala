package responses

import org.specs2.mutable._
import scala.io.Source
import responses._
import parsing._
import parsing.Types._
import parsing.Types.Path._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML
import testHelpers.BeforeAll

class ReadTest extends Specification with BeforeAll {
  implicit val SQLite = new TestDB("read-test")
  val ReadResponseGen = new ReadResponseGen

  def beforeAll = {
    val calendar = Calendar.getInstance()
    calendar.setTime(new Date(1421775723))
    calendar.set(Calendar.HOUR_OF_DAY, 12)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    SQLite.clearDB()
    val testData = Map(
        Path("Objects/ReadTest/Refrigerator123/PowerConsumption") -> "0.123",
        Path("Objects/ReadTest/Refrigerator123/RefrigeratorDoorOpenWarning") -> "door closed",
        Path("Objects/ReadTest/Refrigerator123/RefrigeratorProbeFault") -> "Nothing wrong with probe",
        Path("Objects/ReadTest/RoomSensors1/Temperature/Inside") -> "21.2",
        Path("Objects/ReadTest/RoomSensors1/CarbonDioxide") -> "too much",
        Path("Objects/ReadTest/RoomSensors1/Temperature/Outside") -> "12.2",
        Path("Objects/ReadTest/SmartCar/Fuel") -> "30"
    )

    val intervaltestdata = List(
                            "100",
                            "102",
                            "105",
                            "109",
                            "115",
                            "117")

    for ((path, value) <- testData){
        SQLite.remove(path)
        SQLite.set(new DBSensor(path, value, testtime))
    }

    var count = 0

    //for begin and end testing
    SQLite.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata){
        SQLite.set(new DBSensor(Path("Objects/ReadTest/SmartOven/Temperature"), value, new java.sql.Timestamp(date.getTime + count)))
        count = count + 1000
    }

    //for metadata testing (if i added metadata to existing infoitems the previous tests would fail..)
    SQLite.remove(Path("Objects/Metatest/Temperature"))
    SQLite.set(new DBSensor(Path("Objects/Metatest/Temperature"), "asd", testtime))
    SQLite.setMetaData(Path("Objects/Metatest/Temperature"),
        """<MetaData><InfoItem name="TemperatureFormat"><value dateTime="1970-01-17T12:56:15.723">Celsius</value></InfoItem></MetaData>""")

}

  "Read response" should {
    sequential

    "Give correct XML when asked for multiple values" in {
        lazy val simpletestfile = Source.fromFile("src/test/resources/responses/read/SimpleXMLReadRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/correctXMLfirsttest.xml")
        val parserlist = OmiParser.parse(simpletestfile)
        val resultXML = trim(ReadResponseGen.runGeneration(parserlist.head.asInstanceOf[OneTimeRead]).head)
        
        resultXML should be equalTo(trim(correctxmlreturn))
        OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }

    "Give a history of values when begin and end is used" in {
        lazy val intervaltestfile = Source.fromFile("src/test/resources/responses/read/IntervalXMLTest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/CorrectIntervalXML.xml")
        val parserlist = OmiParser.parse(intervaltestfile)
        val resultXML = trim(ReadResponseGen.runGeneration(parserlist.head.asInstanceOf[OneTimeRead]).head)
        
        resultXML should be equalTo(trim(correctxmlreturn))
        OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }

    "Give object and its children when asked for" in {
        lazy val plainxml = Source.fromFile("src/test/resources/responses/read/PlainRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/PlainRightRequest.xml")

        val parserlist = OmiParser.parse(plainxml)
        val resultXML = trim(ReadResponseGen.runGeneration(parserlist.head.asInstanceOf[OneTimeRead]).head)
        //println(resultXML)

        resultXML should be equalTo(trim(correctxmlreturn))
        OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }

    "Give errors when a user asks for a wrong kind of/nonexisting object" in {
        lazy val erroneousxml = Source.fromFile("src/test/resources/responses/read/ErroneousXMLReadRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/WrongRequestReturn.xml")
        val parserlist = OmiParser.parse(erroneousxml)
        val resultXML = trim(ReadResponseGen.runGeneration(parserlist.head.asInstanceOf[OneTimeRead]).head)
        
        //returnCode should not be 200
        resultXML should be equalTo(trim(correctxmlreturn))
        //OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }

    "Return with correct metadata" in {
        lazy val metarequestxml = Source.fromFile("src/test/resources/responses/read/MetadataRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/MetadataCorrectReturn.xml")
        val parserlist = OmiParser.parse(metarequestxml)
        val resultXML = trim(ReadResponseGen.runGeneration(parserlist.head.asInstanceOf[OneTimeRead]).head)

        resultXML should be equalTo(trim(correctxmlreturn))
    }

}

    "When given path ODFREST" should {

    "Give just the value when path ends with /value" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/Refrigerator123/PowerConsumption/value"))

        RESTXML should be equalTo(Some(Left("0.123")))
    }


    "Give correct XML when asked with an object path and trailing /" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/RoomSensors1/"))


        val rightXML = <Object><id>RoomSensors1</id><InfoItem name="CarbonDioxide"/><Object>
                  <id>Temperature</id>
                </Object></Object>

        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

    "Give correct XML when asked with an InfoItem path and trailing /" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/RoomSensors1/CarbonDioxide"))

        val rightXML = <InfoItem name="CarbonDioxide">
                        <value dateTime="1970-01-17T12:56:15.723">too much</value>
                        </InfoItem>

        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

    "Return None when asked for nonexisting object" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/RoomSensors1/Wrong"))

        RESTXML should be equalTo(None)
    }

    "Return right xml when asked for" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest"))

        val rightXML = <Object><id>ReadTest</id><Object><id>Refrigerator123</id></Object><Object><id>RoomSensors1</id></Object><Object><id>SmartCar</id></Object>
                        <Object><id>SmartOven</id></Object></Object>

        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

  }
}
