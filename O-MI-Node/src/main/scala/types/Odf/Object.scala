package types
package odf

import database.journal.PersistentNode

import scala.language.implicitConversions
import scala.util.Try
import scala.collection.{Map, Seq}
import scala.collection.immutable.{HashMap, Map => IMap}
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.{InfoItemType, ObjectType}

case class Object(
  val ids: Vector[QlmID],
  val path: Path,
  val typeAttribute: Option[String] = None,
  val descriptions: Set[Description] = Set.empty,
  val attributes: IMap[String,String] = HashMap.empty
) extends Node with Unionable[Object] {
  assert( ids.nonEmpty, "Object doesn't have any ids.")
  assert( path.length > 1, "Length of path of Object is not greater than 1 (Objects/).")
  def idsToStr() = ids.toList.map{ 
    id: QlmID => 
      id.id
  }.toVector

  def idTest = idsToStr.exists{
    id: String => 
      val pl = path.last
      id == pl
  }
  def tmy =  s"Ids don't contain last id in path. ${ path.last } not in (${idsToStr.mkString(",")})"
  assert( idTest, tmy)
  def update( that: Object ): Object ={
    val pathsMatches = path == that.path 
    val containSameId = ids.map( _.id ).toSet.intersect( that.ids.map( _.id).toSet ).nonEmpty
    assert( containSameId && pathsMatches)
    Object(
      QlmID.unionReduce(ids ++ that.ids).toVector,
      path,
      that.typeAttribute.orElse(typeAttribute),
      Description.unionReduce(descriptions ++ that.descriptions).toSet,
      attributes ++ that.attributes
    )
    
  }

  def hasStaticData: Boolean ={
    attributes.nonEmpty ||
    ids.length > 1 ||
    typeAttribute.nonEmpty ||
    descriptions.nonEmpty 
  }
  def union( that: Object ): Object ={
    val pathsMatches = path == that.path 
    val containSameId = ids.map( _.id ).toSet.intersect( that.ids.map( _.id).toSet ).nonEmpty
    assert( containSameId && pathsMatches)
    Object(
      QlmID.unionReduce(ids ++ that.ids).toVector,
      path,
      optionAttributeUnion(typeAttribute, that.typeAttribute),
      Description.unionReduce(descriptions ++ that.descriptions).toSet,
      attributeUnion(attributes, that.attributes)
    )
    
  }
  def createAncestors: Seq[Node] = {
    path.getAncestors.collect{
      case ancestorPath: Path if ancestorPath.nonEmpty => 
        if( ancestorPath == Path("Objects")){
          Objects()
        } else {
          Object(
            Vector(
              new QlmID(
                ancestorPath.last
              )
            ),
          ancestorPath
        )
        }
    }.toVector
  }
  def createParent: Node = {
    val parentPath = path.getParent
    if( parentPath.isEmpty || parentPath == Path( "Objects") ){
      Objects()
    } else {
      Object(
        Vector(
          QlmID(
            parentPath.last
          )
        ),
        parentPath
      )
    }
  }

  implicit def asObjectType( infoitems: Seq[InfoItemType], objects: Seq[ObjectType] ) : ObjectType = {
    ObjectType(
      /*Seq( QlmID(
        path.last, // require checks (also in OdfObject)
        attributes = Map.empty
      )),*/
      ids.map(_.asQlmIDType), //
      descriptions.map(des => des.asDescriptionType).toVector,
      infoitems,
      objects,
      attributes = (
        attributesToDataRecord(attributes) ++ typeAttribute.map { n => "@type" -> DataRecord(n) })
    )
  }

  def readTo(to: Object): Object ={
    val pathsMatches = path == to.path 
    val containSameId = ids.map( _.id ).toSet.intersect( to.ids.map( _.id).toSet ).nonEmpty
    assert( containSameId && pathsMatches)

    val desc = if( to.descriptions.nonEmpty ) {
      val languages = to.descriptions.flatMap(_.language)
      if( languages.nonEmpty ){
        descriptions.filter{
          case Description(text,Some(lang)) => languages.contains(lang)
          case Description(text,None) => true
        }
      } else {
        descriptions
      }
    } else if( this.descriptions.nonEmpty){
      Vector(Description("",None))
    } else Vector.empty
    //TODO: Filter ids based on QlmID attributes
    to.copy( 
      ids = QlmID.unionReduce(ids ++ to.ids).toVector,
      typeAttribute = typeAttribute.orElse(to.typeAttribute),
      descriptions = desc.toSet,
      attributes = attributes ++ to.attributes
    )
  }

  def persist: PersistentNode = ??? //PersistentNode(isObject = true, path = path.toString, attributes = attributes)
}
