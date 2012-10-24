package scala.slick.migrations

import collection.immutable.HashMap

trait MigrationBase {
  def up
  def down
}
trait NamedMigrationBase extends MigrationBase{
  def id:String
}
abstract class Migration( val id:String ) extends NamedMigrationBase{
  def up
  def down
  override def toString = s"Migration(${id})"
}
class NoMigrationPathFound extends Exception
/*
 * TODO:
 * - add upgradable/downgradable interface
 * - allow reporting about each up/down in some way
 */
trait MigrationManagerBase[Migration <: MigrationBase] {
  protected def lastMigration : Migration
  protected def lastMigration_=(value:Migration) : Unit
  protected val _migrations : List[Migration]
  private def migrationPath( migrations_ :List[Migration], to:Migration ) = {
    migrations_.dropWhile(_ != lastMigration )
               .takeWhile(_ != to )
               .:+( to )
  }
  protected def up( migration:Migration ) = migration.up
  protected def down( migration:Migration ) = migration.down
  def upgradeTo( to:Migration ) : Unit = {
    if( to == lastMigration )
      return;
    if( !upgradePathExists(lastMigration,to) )
      throw new NoMigrationPathFound
    migrationPath( _migrations, to ).drop(1).foreach( up(_) )
    lastMigration = to
  }
  private def downgradePathExists( from:Migration, to:Migration ) : Boolean =
    upgradePathExists(to, from)
  private def upgradePathExists( from:Migration, to:Migration ) = {
    /*println(_migrations)
    println(   ( _migrations.contains(from) ,
      _migrations.contains(to) ,
      _migrations.indexOf(to) , _migrations.indexOf(from))
    )*/
    _migrations.contains(from) &&
      _migrations.contains(to) &&
      _migrations.indexOf(to) >= _migrations.indexOf(from)
  }
  def downgradeTo( to:Migration ) : Unit = {
    if( to == lastMigration )
      return;
    if( !downgradePathExists(lastMigration, to) )
      throw new NoMigrationPathFound
    val path = migrationPath( _migrations.reverse, to )
    path.take(path.length-1).foreach( down(_) )
    lastMigration = to
  }
}
trait NamedMigrationManagerBase[Migration <: NamedMigrationBase] extends MigrationManagerBase[Migration]{
  def initial : Migration
  protected def migrations : List[Migration]
  protected val _migrations :List[Migration] = initial :: migrations
  val byName = HashMap( _migrations.map( m => m.id -> m ) : _* )
  def upgradeTo( to:String ) : Unit = upgradeTo(byName(to))
  def downgradeTo( to:String ) : Unit = downgradeTo(byName(to))
}
