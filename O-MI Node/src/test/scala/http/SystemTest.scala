package http
import org.specs2.mutable._
import spray.http._
import spray.client.pipelining._
import akka.actor.{ ActorRef, ActorSystem, Props }
import scala.concurrent._
import scala.xml._
import scala.util.Try
import parsing._
import testHelpers.{ BeEqualFormatted, HTML5Parser, SystemTestCallbackServer }
import database._
import testHelpers.AfterAll
import responses.{ SubscriptionHandler, RequestHandler }
import agentSystem.ExternalAgentListener
import scala.concurrent.duration._
import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import java.net.InetSocketAddress
import org.specs2.specification.Fragments
import java.text.SimpleDateFormat
import java.util.{ TimeZone, Locale }
import akka.testkit.{TestProbe, EventFilter}
import akka.testkit.TestEvent.{Mute, UnMute}
import spray.can.Http
import com.typesafe.config.ConfigFactory
import org.xml.sax.InputSource

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SystemTest extends Specification with Starter with AfterAll {
  
            
  override def start(dbConnection: DB = new DatabaseConnection): ActorRef = {
    val subHandler = system.actorOf(Props(new SubscriptionHandler()(dbConnection)), "subscription-handler")
    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")

    // do not start InternalAgents to keep database clean
    //    val agentLoader = system.actorOf(InternalAgentLoader.props() , "agent-loader")

    // create omi service actor
    val omiService = system.actorOf(Props(new OmiServiceActor(new RequestHandler(subHandler)(dbConnection))), "omi-service")

    implicit val timeoutForBind = Timeout(Duration.apply(5, "second"))

    IO(Tcp) ? Tcp.Bind(sensorDataListener, new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))

    return omiService
  }

  sequential
  
  import system.dispatcher


  //start the program
  implicit val dbConnection = new TestDB("SystemTest")

  init(dbConnection)
  val serviceActor = start(dbConnection)
  bindHttp(serviceActor)

  val probe = TestProbe()
  lazy val testServer = system.actorOf(Props(classOf[SystemTestCallbackServer], probe.ref))
  IO(Http) ! Http.Bind(testServer, interface = "localhost", port = 20002)

  val pipeline: HttpRequest => Future[NodeSeq] = sendReceive ~> unmarshal[NodeSeq]
  val printer = new scala.xml.PrettyPrinter(80, 2)
  val parser = new HTML5Parser
  val sourceFile = if(java.nio.file.Files.exists(java.nio.file.Paths.get("O-MI Node/html/ImplementationDetails.html"))){
    Source.fromFile("O-MI Node/html/ImplementationDetails.html")
  }else Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)
  val testArticles = sourceXML \\ ("article")
  val tests = testArticles.groupBy(x => x.\@("class"))

  //tests with request response pairs, each containing description and forming single test(req, resp), (req, resp)...
  lazy val readTests = tests("request-response single test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length == 2, s"Each request must have exactly 1 response in request-response tests, could not find for: $node")
    val request: Try[Elem] = getSingleRequest(textAreas)
    val correctResponse: Try[Elem] = getSingleResponse(textAreas)
    val testDescription = node \ ("div") \ ("p") text

    (request, correctResponse, testDescription)
  }

  //tests that have multiple request-response pairs that all have common description (req, resp, req, resp...)
  lazy val subsNoCallback = tests("request-response test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length % 2 == 0, "There must be even amount of response and request messages(1 response for each request)\n" + textAreas)

    val testDescription: String = node \ ("div") \ ("p") text

    val groupedRequests = textAreas.grouped(2).map { reqresp =>
      val request: Try[Elem] = getSingleRequest(reqresp)
      val correctresponse: Try[Elem] = getSingleResponseNoTime(reqresp)
      val responseWait: Option[Int] = Try(reqresp.last.\@("wait").toInt).toOption
      (request, correctresponse, responseWait)
    }
    (groupedRequests, testDescription)

  }
  /*
   * tests that have callback server responses mixed:
   * for example if the test in html is (req, resp, callbackresp, callbackresp, req, resp)
   * this groups them like: (req, resp), (callbackresp), (callbackresp), (req,resp)
   * so that each req resp pair forms 1 test and callbackresp alone forms 1 test
   */
  lazy val sequentialTest = tests("sequential-test").map { node =>
    val textAreas = node \\ ("textarea")
    val testDescription: String = node \ ("div") \ ("p") text
    val reqrespCombined: Seq[NodeSeq] = textAreas.foldLeft[Seq[NodeSeq]](NodeSeq.Empty) { (res, i) =>
      if (res.isEmpty) i
      else {
        if (res.last.length == 1 && (res.last.head.\@("class") == "request")) {
          val indx: Int = res.lastIndexWhere { x => x.head.\@("class") == "request" }
          res.updated(indx, res.last.head :+ i)

        } else res.:+(i) 
      }
    }

    (reqrespCombined, testDescription)
  }

  def afterAll = {
    
    system.shutdown()
    dbConnection.destroy()

  }

  def getSingleRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.head.text)))
  }

  def getSingleResponseNoTime(reqresp: NodeSeq): Try[Elem] = {
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.last.text.replaceAll("""unixTime\s*=\s*"\d*"""", ""))))
  }
  def getSingleResponse(reqresp: NodeSeq): Try[Elem] = {
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.last.text)))
  }
  def getCallbackRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.head.text.replaceAll("""callback\s*=\s*"(http:\/\/callbackAdrress\.com:5432)"""", """callback="http://localhost:20002/""""))))
  }

  def setTimezoneToSystemLocale(in: String): String = {
    val date = """(end|begin)\s*=\s*"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})"""".r

    val replaced = date replaceAllIn (in, _ match {

      case date(pref, timestamp) => {

        val form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        form.setTimeZone(TimeZone.getTimeZone("UTC"))

        val parsedTimestamp = form.parse(timestamp)

        form.setTimeZone(TimeZone.getDefault)

        val newTimestamp = form.format(parsedTimestamp)

        (pref + "=\"" + newTimestamp + "\"")
      }
    })
    replaced
  }

  "Automatic System Tests" should {
    "Write Test" >> {
      dbConnection.clearDB()
      //Only 1 write test
      val writearticle = tests("write test").head
      val testCase = writearticle \\ ("textarea")
      val request: Try[Elem] = getSingleRequest(testCase)
      val correctResponse: Try[Elem] = getSingleResponse(testCase)
      val testDescription = writearticle \ ("div") \ ("p") text

      ("test case:\n " + testDescription.trim + "\n") >> {
        request aka "Write request message" must beSuccessfulTry
        correctResponse aka "Correct write response message" must beSuccessfulTry

        val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
        val response = Try(Await.result(responseFuture, Duration(2, "second")))

        response must beSuccessfulTry

        response.get showAs (n =>
          "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
      }
    }

    step({
      Thread.sleep(2000);
    })
    
    "Read Test" >> {
      readTests.foldLeft(Fragments())((res, i) => {
        val (request, correctResponse, testDescription) = i
        res.append(
          (testDescription.trim + "\n") >> {

            request aka "Read request message" must beSuccessfulTry
            correctResponse aka "Correct read response message" must beSuccessfulTry

            val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
            val response = Try(Await.result(responseFuture, Duration(2, "second")))

            response must beSuccessfulTry

            response.get showAs (n =>
              "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
          })
      })
    }

    "Subscription Test" >> {
      subsNoCallback.foldLeft(Fragments())((res, i) => {
        val (reqrespwait, testDescription) = i
        (testDescription.trim() + "\n") >> {

          reqrespwait.foldLeft(Fragments())((res, j) => {
            "step: " >> {

              val (request, correctResponse, responseWait) = j
              request aka "Subscription request message" must beSuccessfulTry
              correctResponse aka "Correct response message" must beSuccessfulTry

              responseWait.foreach { x => Thread.sleep(x * 1000) }

              val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
              val responseXml = Try(Await.result(responseFuture, Duration(2, "second")))

              responseXml must beSuccessfulTry
              
              val response = XML.loadString(responseXml.get.toString.replaceAll("""unixTime\s*=\s*"\d*"""", ""))

              response showAs (n =>
                "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
            }
          })
        }

      })
    }
    "Callback Test" >> {
      sequentialTest.foldLeft(Fragments())((res, i) => {

        val (singleTest, testDescription) = i

        (testDescription.trim()) >> {
          singleTest.foldLeft(Fragments())((res, j) => {
            "Step: " >> {

              require(j.length == 2 || j.length == 1)
              val correctResponse = getSingleResponseNoTime(j)
              val responseWait: Option[Int] = Try(j.last.\@("wait").toInt).toOption

              correctResponse aka "Correct response message" must beSuccessfulTry

              if (j.length == 2) {
                val request = getCallbackRequest(j)

                request aka "Subscription request message" must beSuccessfulTry

                responseWait.foreach { x => Thread.sleep(x * 1000) }

                val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
                val responseXml = Try(Await.result(responseFuture, Duration(2, "second")))
             

                responseXml must beSuccessfulTry
              
              val response = XML.loadString(responseXml.get.toString.replaceAll("""unixTime\s*=\s*"\d*"""", ""))
                //remove blocking waits if possible
                if(request.get.\\("write").nonEmpty){
                  Thread.sleep(2000)
                }
                response showAs (n =>
                  "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)

              } else {
                val messageOption = probe.expectMsgType[Option[NodeSeq]](Duration(responseWait.getOrElse(2), "second"))
                
                messageOption must beSome
                val response = XML.loadString(messageOption.get.toString().replaceAll("""unixTime\s*=\s*"\d*"""", ""))
                
                response showAs (n =>
                  "Response at callback server:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)

              }
            }
          })
        }
      })
    }
  }
}
