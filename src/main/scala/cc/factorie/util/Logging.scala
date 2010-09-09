/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie.util

import java.io._

/** Class for logging messages.  
    If you want to globally replace this mechanism with a different one, you can change 
    "type Logging = cc.factorie.util.Logging" 
    in cc/factorie/package.scala to something else, and recompile.  The logging method names used in the library are pretty standard
    @author Andrew McCallum  */
class Logger(val name:String, outputStream: => OutputStream = System.err, @volatile var level: Int = Logger.WARN) {
  def this(name:String, f:File) = this (name, new BufferedOutputStream(new FileOutputStream(f)))
  def this(name:String, s:String) = this (name, new File(s))
  /**  Try to parse the level string into an Int from System.getenv, but if it fails to parse, do nothing.  
       Return true if level was set, false otherwise. */
  def setLevelFromEnv: Boolean = {
    val envLevelString = java.lang.System.getenv(name+".level")
    if (envLevelString != null) try { level = envLevelString.toInt } catch { case _ => return false }
    else return false
    return true
  }
  setLevelFromEnv
  if (Logger.loggerMap.contains(name)) throw new Error("There is already a logger named "+name)
  Logger.loggerMap(name) = this
  private val out = new PrintWriter(outputStream);
  def close() = out.close()
  def verbosePrefix(theLevel: Int): String = "["+theLevel+"] ("+Logger.sourceDescription(2)+") "
  def prefix(theLevel: Int): String = ""
  def log(theLevel: Int)(msg: => Any): Unit = {
    if (level >= theLevel) {
      out.println(prefix(theLevel) + msg.toString)
      out.flush()
    }
  }
  def fatal(msg: =>Any): Unit = log(Logger.FATAL)(msg)
  def error(msg: =>Any): Unit = log(Logger.ERROR)(msg)
  def warn(msg: =>Any): Unit = log(Logger.WARN)(msg)
  def info(msg: =>Any): Unit = log(Logger.INFO)(msg)
  def config(msg: =>Any): Unit = log(Logger.CONFIG)(msg)
  def fine(msg: =>Any): Unit = log(Logger.FINE)(msg)
  def finer(msg: =>Any): Unit = log(Logger.FINER)(msg)
  def finest(msg: =>Any): Unit = log(Logger.FINEST)(msg)
  def debug(msg: =>Any): Unit = log(Logger.DEBUG)(msg)
  def trace(msg: =>Any): Unit = log(Logger.TRACE)(msg)
}

object Logger {
  val loggerMap = new scala.collection.mutable.HashMap[String,Logger]
  val globalLogger = new Logger("cc.factorie", System.err, WARN)
  val neverLogger = new Logger("null", System.err, Int.MinValue) {
    override def log(theLevel: Int)(x: => Any): Unit = {}
  }
  def logger(name:String) = loggerMap.getOrElseUpdate(name, new Logger(name, System.err, WARN))
  val NEVER = -1
  val FATAL = 10
  val ERROR = 20
  val WARN = 30
  val INFO = 40
  val CONFIG = 50
  val FINE = 60
  val FINER = 70
  val FINEST = 80
  val DEBUG = 90
  val TRACE = 100

  @noinline protected def sourceDescription(framesUp:Int): String = {
    val e = new Exception().getStackTrace()(framesUp+1);
    e.getFileName() + ":" + e.getLineNumber();
  }

}

/** Uses no per-instance memory, but slow because it does a hash lookup for each call on the logger. */
trait Logging {
  def logger: Logger = Logger.logger(this.getClass.getName)
}

/** Uses no per-instance memory, and is fast because it goes directly to the companion class to get a logger, 
    but it is the default logger for all "cc.factorie", and you can't set its level individually. */
trait GlobalLogging extends Logging {
  override def logger = Logger.globalLogger
}

/** Fast, but uses one machine word of memory per-instance. */
trait FastLogging extends Logging {
  override val logger = Logger.logger(this.getClass.getName)
}

