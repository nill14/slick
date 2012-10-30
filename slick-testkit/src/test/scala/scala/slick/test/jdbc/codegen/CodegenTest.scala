package scala.slick.test.jdbc.codegen

import java.io.PrintWriter
import org.junit.Test
import org.junit.Assert._
import scala.slick.lifted.TypeMapper._
import scala.slick.driver.{H2Driver, PostgresDriver}
import scala.slick.jdbc.meta._
import scala.slick.session.Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import scala.slick.testutil._
import scala.slick.testutil.TestDBs._
import com.typesafe.slick.testkit.util.TestDB
import scala.slick.jdbc

object CodegenTest extends DBTestObject(H2Mem)//, SQLiteMem, Postgres, MySQL, DerbyMem, HsqldbMem, SQLServer)

class CodegenTest(val tdb: TestDB) extends DBTest {
  import tdb.profile.Table
  import tdb.profile.Implicit._

  object Users extends Table[(Int, String, Option[String])]("users") {
    def id = column[Int]("id", O.PrimaryKey)
    def first = column[String]("first", O Default "NFN", O DBType "varchar(64)")
    def last = column[Option[String]]("last")
    def * = id ~ first ~ last
  }

  object Orders extends Table[(Int, Int, String, Boolean, Option[Boolean])]("orders") {
    def userID = column[Int]("userID")
    def orderID = column[Int]("orderID", O.PrimaryKey)
    def product = column[String]("product")
    def shipped = column[Boolean]("shipped", O Default false)
    def rebate = column[Option[Boolean]]("rebate", O Default Some(false))
    def * = userID ~ orderID ~ product ~ shipped ~ rebate
    def userFK = foreignKey("user_fk", userID, Users)(_.id)
  }

  @Test def test() {

    db withSession {
      val ddl = (Users.ddl ++ Orders.ddl)
      println("DDL used to create tables:")
      for(s <- ddl.createStatements) println("  "+s)
      ddl.create
      
      val generator = new scala.slick.jdbc.codegen.Schema(
        new scala.slick.jdbc.reflect.Schema((List("users","orders"))),
        "foo.entities"
      )
      
      println( generator.render )
    }
  }
}
