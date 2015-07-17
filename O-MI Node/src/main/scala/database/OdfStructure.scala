package database
import types._
import types.Path._
import types.OdfTypes._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
import scala.collection.mutable.{ Map ,HashMap, PriorityQueue, HashSet}
import java.sql.Timestamp
import java.util.Date

class OdfStructure {
  implicit val dbConnection: DB
  protected var OdfTree : OdfObjects = OdfObjects()
  protected var PathMap : collection.mutable.Map[String, OdfNode] = HashMap.empty
  protected var PathToHierarchyId : collection.mutable.Map[String, Int] = HashMap.empty
  protected var NormalSubs : collection.mutable.HashMap[Int, Seq[Path]] = HashMap.empty
  protected var PolledPaths : collection.mutable.HashMap[Int, Seq[Path]] = HashMap.empty
  case class Poll(id: Int, timestamp: Timestamp)
  protected var HierarchyIdPollQueues : collection.mutable.Map[Int,collection.mutable.PriorityQueue[Poll]] = HashMap.empty
  def PollQueue =new  PriorityQueue[Poll]()(
    Ordering.by{poll : Poll => poll.timestamp.getTime}
  )
  protected var EventSubs : collection.mutable.HashMap[Path, Seq[Int]] = HashMap.empty

  def addOrUpdate( odfNodes: Seq[OdfNode] ) ={
    val tmpUpdates : (OdfObjects, Seq[(Path, OdfNode)])= odfNodes.map(fromPath(_)).foldLeft((OdfObjects(), Seq[(Path,OdfNode)]())){
      (a, b) =>
      val updated = a._1.update(b)
      (updated._1,updated._2 ++ a._2) 
    }
    val (objects, updateTuples) = OdfTree.update(tmpUpdates._1)
    val updated = (updateTuples ++ tmpUpdates._2).toSet.toSeq
    PathMap ++= updated.map{a => (a._1.toString,a._2)}

    //Todo DB update and adding, and hierarchy ids
    val pathValueTuples = odfNodes.collect{ 
      case info : OdfInfoItem => info 
    }.flatMap{
      info => info.values.map{ value => (info.path, value ) } 
    }
    val newNodes = odfNodes.filterNot{ node => PathToHierarchyId.contains(node.path.toString)}
    val newPathIdTuples = dbConnection.addNodes( newNodes ).map{ (path, id) => (path.toString, id)}
    PathToHierarchyId ++= newPathIdTuples 
    val updatedIds = dbConnection.setMany(pathValueTuples.toList)
    //TODO: Trigger event sub responsese  

    ???
  } 
  def get(odfNodes: Seq[OdfNode]) : OdfObjects ={
    odfNodes.map{
      node => 
      (node, OdfTree.get(node.path)) match{
        case (requested: OdfObjects, Some(found: OdfObjects)) =>
          found
        case (requested: OdfObject, Some(found: OdfObject)) =>
          fromPath(found)
        case (requested: OdfInfoItem, Some(found: OdfInfoItem)) =>
          fromPath(found)
        case (requested,found) => 
          throw new Exception(s"Mismatch ${requested.getClass} is not ${found.getClass}, path: ${requested.path}")       
      }
    }.foldLeft(OdfObjects())(_.combine(_))
  }
  
  def getNBetween(
    odfNodes: Seq[OdfNode], 
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int] ) : Option[OdfObjects] = {
    val hIds = getHierarchyIds(odfNodes)
    //TODO: DATABASE METHOD
    def getNBetween(
      hIds: Set[Int],
      begin: Option[Timestamp],
      end: Option[Timestamp],
      newest: Option[Int],
      oldest: Option[Int]
    ) : Option[OdfObjects]  = ???

    getNBetween( hIds, begin, end, newest, oldest)
  }

  private def getHierarchyIds( odfNodes: Seq[OdfNode] ) : Set[Int] = {
    getLeafs(get(odfNodes)).map{
      leaf => PathToHierarchyId.get(leaf.path.toString)
    }.collect{case Some(id) => id }.toSet
  }

  def getNormalSub(id: Int) : Option[OdfObjects] ={
    NormalSubs.get(id).map{
      paths =>
      paths.map(path => PathMap.get( path.toString )).collect{ 
        case Some(node) => fromPath( node  )
      }.foldLeft(OdfObjects()){
        (a, b) =>
        val (updated,_) = a.update(b)
        updated   
      }
    }
  }
  
  def getPolledPaths(id: Int) ={
    PolledPaths.get(id)
  }
  
  def getPolledHierarchyIds(id: Int) ={
    PolledPaths.get(id).map{
      paths => getHierarchyIds(
        paths.map{
          path => PathMap.get(path.toString)
        }.collect{ case Some(node) => node } 
      )
    }
  }

  def getPollData(pollId: Int) : Option[OdfObjects]= {
    //TODO: DATABASE METHOD
    def removeBefore(tuples: Set[(Int, Timestamp)]) = ??? 
    val timestamp = new Timestamp(new Date().getTime)
    def getPollDataWithHiearachyIds(pollId:Int,hierarchyIds: Set[Int]) = ??? 
    getPolledHierarchyIds(pollId).map{
      hIds =>
      val result = getPollDataWithHiearachyIds(pollId,hIds) 
      val removes = hIds.map{
        hId =>
       HierarchyIdPollQueues.get(hId).map{
          pollQueue => 
          (
            pollQueue.headOption.collect{
              case headPoll if headPoll.id != pollId => 
              pollQueue.dequeue
              pollQueue.headOption.map{ poll => poll.timestamp }
            }.collect{ case Some(time) => time},
            pollQueue.filter{ poll => poll.id != pollId } += Poll(pollId, timestamp)
          )
        }.collect{
          case (Some(time), newQueue) =>
          HierarchyIdPollQueues.update(hId,newQueue)
          (hId, time)
        }
      }.collect{case Some(tuple) => tuple }
      Future{ removeBefore(removes) }
      result
    } 
  }

}
