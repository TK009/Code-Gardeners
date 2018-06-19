package database.journal

import java.sql.Timestamp

import database.{EventSub, IntervalSub, PollSub, PolledSub}
import database.journal.PPersistentNode.NodeType.{Ii, Obj, Objs}
import types.Path
import types.odf.{Description, ImmutableODF, InfoItem, MetaData, ODFValue, Object, Objects, QlmID}

object Models {

  sealed trait PersistentMessage

  /**
    * Event is a type for Event classes generated by protobuf.
    * Event classes are located in O-MI-Node/target/scala-2.11/src_managed/main/database.journal/
    */
  trait Event extends PersistentMessage
  //trait PersistentNode
  trait PersistentSub extends PersistentMessage
  sealed trait Command

  //PersistentCommands are classes that need to be serialized
  sealed trait PersistentCommand extends Command with PersistentMessage

  //Latest store
  case class SingleWriteCommand(path: Path, value: ODFValue) extends PersistentCommand

  case class WriteCommand(paths: Map[Path, ODFValue]) extends PersistentCommand

  case class ErasePathCommand(path: Path) extends PersistentCommand

  case class SingleReadCommand(path: Path) extends Command

  case class MultipleReadCommand(paths: Seq[Path]) extends Command

  case object ReadAllCommand extends Command

  //Hierarchy store
  case class UnionCommand(other: ImmutableODF) extends PersistentCommand

  case object GetTree extends Command

  //Sub store
  case class AddEventSub(eventSub: EventSub) extends PersistentCommand

  case class AddIntervalSub(intervalSub: IntervalSub) extends PersistentCommand

  case class AddPollSub(pollsub: PolledSub) extends PersistentCommand

  case class LookupEventSubs(path: Path) extends Command

  case class LookupNewEventSubs(path: Path) extends Command

  case class RemoveIntervalSub(id: Long) extends PersistentCommand

  case class RemoveEventSub(id: Long) extends PersistentCommand

  case class RemovePollSub(id: Long) extends PersistentCommand

  case class PollSubCommand(id: Long) extends PersistentCommand

  case object GetAllEventSubs extends Command

  case object GetAllIntervalSubs extends Command

  case object GetAllPollSubs extends Command

  case class GetIntervalSub(id: Long) extends Command

  case class GetSubsForPath(path: Path) extends Command

  case class GetNewEventSubsForPath(path: Path) extends Command

  //PollData
  case class AddPollData(subId: Long, path: Path, value: ODFValue) extends PersistentCommand

  case class PollEventSubscription(subId: Long) extends PersistentCommand

  case class PollIntervalSubscription(subId: Long) extends PersistentCommand

  case class RemovePollSubData(subId: Long) extends PersistentCommand

  case class CheckSubscriptionData(subId: Long) extends Command



  def buildInfoItemFromProtobuf(pinfo: PInfoItem): InfoItem = {
    val path = Path(pinfo.path)
    InfoItem(
      path.last,
      path,
      Option(pinfo.typeName).filter(_.nonEmpty),
      pinfo.names.map(id => buildQlmIDFromProtobuf(id)).toVector,
      pinfo.descriptions.map(d => Description(d.text, Option(d.lang).filter(_.nonEmpty))).toSet,
      metaData = pinfo.metadata.map(md => MetaData(md.infoItems.map(pi => buildInfoItemFromProtobuf(pi)).toVector)),
      attributes = pinfo.attributes
    )
  }

  def buildQlmIDFromProtobuf(pqlmid: PQlmid): QlmID = {
    QlmID(
      pqlmid.id,
      Option(pqlmid.idType).filter(_.nonEmpty),
      Option(pqlmid.tagType).filter(_.nonEmpty),
      pqlmid.startTime.map(time => new Timestamp(time.time)),
      pqlmid.endTime.map(time => new Timestamp(time.time)),
      pqlmid.attirbutes
    )
  }

  def buildObjectFromProtobuf(pobj: PObject): Object = {
    Object(
      pobj.ids.map(id => buildQlmIDFromProtobuf(id)).toVector,
      Path(pobj.path),
      Option(pobj.typeName).filter(_.nonEmpty),
      pobj.descriptions.map(d => Description(d.text, Option(d.lang).filter(_.nonEmpty))).toSet,
      pobj.attributes

    )
  }

  def buildObjectsFromProtobuf(pobjs: PObjects): Objects = {
    Objects(
      Option(pobjs.version).filter(_.nonEmpty),
      pobjs.attributes
    )
  }

  def buildImmutableOdfFromProtobuf(in: Map[String, PPersistentNode]): ImmutableODF = {
    ImmutableODF(
      in.map { case (k, v) =>
        v.nodeType match {
          case Ii(pinfo) =>
            buildInfoItemFromProtobuf(pinfo)
          case Obj(pobject) =>
            buildObjectFromProtobuf(pobject)
          case Objs(pobjects) =>
            buildObjectsFromProtobuf(pobjects)

        }
      }
    )
  }

  def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
    val (bigger, smaller) = if (a.size > b.size) (a, b) else (b, a)
    smaller.foldLeft(bigger) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
  }

  def mergeSubs(a: Map[Path, Seq[EventSub]], b: Map[Path, Seq[EventSub]]): Map[Path, Seq[EventSub]] = {
    merge(a, b) { case (v1, v2) =>
      v2.map(subs => subs ++ v1).getOrElse(v1)
    }
  }
}

