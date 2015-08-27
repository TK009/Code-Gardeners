/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package responses

import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb
import types.OdfTypes.OdfObjects
import scala.xml.{XML, Node}
import scala.language.existentials
import OmiGenerator.{omiResult, omiReturn, odfMsg}
import parsing.xmlGen.defaultScope

/** 
  * Object containing helper mehtods for generating RequestResultTypes. Used to generate results  for requests.
  *
  **/
object Results{

  /** O-MI result for request that caused internal server error. 
    * @return O-MI result tag.
    **/
  def internalError(msg: String = "Internal error") : RequestResultType = simple( "500", Some(msg) )
  /** O-MI result for features that aren't implemented.
    * @param msg 
    * @return O-MI result tag.
    **/
  def notImplemented : RequestResultType = simple( "501", Some("Not implemented") )
  /** O-MI result for reuqest which permission was denied.
    * @return O-MI result tag.
    **/
  def unauthorized : RequestResultType = simple( "401", Some("Unauthorized") )
  /** O-MI result for not found O-DF nodes.
    * @return O-MI result tag.
    **/
  def notFound: RequestResultType = simple( "404", Some("Such item/s not found.") )
  /** O-MI result for not found Subscription with out requestID.
    * @return O-MI result tag.
    **/
  def notFoundSub: RequestResultType = simple( "404", Some("A subscription with this id has expired or doesn't exist"))
  /** O-MI result for not found Subscription with requestID. 
    * @param requestID of subscription not found.
    * @return O-MI result tag.
    **/
  def notFoundSub(requestID: String): RequestResultType =
    omiResult(
      omiReturn( "404", Some("A subscription with this id has expired or doesn't exist")),
      Some(requestID)
    )
    
  /** O-MI result for successful request. 
    * @return O-MI result tag.
    **/

  def success : RequestResultType = simple( "200", None)

  /** O-MI result for invalid request. 
    * @param msg reason for invalidation.
    * @return O-MI result tag.
    **/
  def invalidRequest(msg: String = ""): RequestResultType = simple( "400", Some("Bad request: " + msg) )


  /**  for normal O-MI Read request.
    *
    * @param objects objects contains O-DF data read from database
    * @return  containing the O-DF data. 
    **/
  def read( objects: OdfObjects) : RequestResultType =  odf( "200", None, None, objects)

  /**  for poll request, O-MI Read request with requestID.
    *
    * @param requestID  requestID of subscription
    * @param objects objects contains O-DF data read from database
    * @return  containing the requestID and the O-DF data. 
    **/
  def poll( requestID: String, objects: OdfObjects) : RequestResultType =
    odf( "200", None, Some(requestID), objects)

  /**  for interval Subscription to use when automatily sending responses to callback address.
    *
    * @param requestID  requestID of subscription
    * @param objects objects contains O-DF data read from database
    * @return  containing the requestID and the O-DF data. 
    **/
  def subData( requestID: String, objects: OdfObjects) : RequestResultType =
    odf( "200", None, Some(requestID), objects)  
    
  /**  for subscripton request, O-MI Read request with interval.
    *
    * @param requestID  requestID of created subscription
    * @return  containing the requestID 
    **/
  def subscription( requestID: String): RequestResultType ={
    omiResult(
      omiReturn(
        "200",
        Some("Successfully started subscription")
      ),
      Some(requestID)
    )
  }

  /** Generates O-MI result tag  containing O-DF formated msg tag.
    * @param code HTTP return code as String.
    * @param description for HTTP return code.
    * @param requestID for subscriptions.
    * @param objects OdfObjects, O-DF formatted data.
    * @return O-MI result tag.
    **/
  def odf( returnCode: String, returnDescription: Option[String], requestID: Option[String], objects: OdfObjects): RequestResultType  = {
    omiResult(
      omiReturn(
        returnCode,
        returnDescription
      ),
      requestID,
      Some("odf"),
      Some( odfMsg( scalaxb.toXML[ObjectsType]( objects.asObjectsType , None, Some("Objects"), defaultScope ) ) ) 
    )
  }

  /** Simple result containing only status code and optional description.
    * @param code HTTP return code as String.
    * @param description for HTTP return code.
    * @return O-MI result tag.
    **/
  def simple(code: String, description: Option[String] ) : RequestResultType = {
    omiResult(
      omiReturn(
        code,
        description
      )
    )
  }

}
import Results._
    /** Package needs one class to have documentation, sbt doc fails*/
    case class DocumentationForcer()
