/* Copyright (C) 2008-2016 University of Massachusetts Amherst.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://github.com/factorie
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package cc.factorie.app.nlp.relation

import java.io.FileInputStream

import cc.factorie.app.nlp.coref.ParseForwardCoref
import cc.factorie.app.nlp.load.LoadOWPL
import cc.factorie.app.nlp.ner.{NerTag, NoEmbeddingsConllStackedChainNer}
import cc.factorie.app.nlp.parse.OntonotesTransitionBasedParser
import cc.factorie.app.nlp.phrase.Phrase
import cc.factorie.app.nlp.pos.OntonotesForwardPosTagger
import cc.factorie.app.nlp.{Document, DocumentAnnotatorPipeline, Token, TokenSpan}
import cc.factorie.variable.{CategoricalDomain, MutableCategoricalVar}

import scala.collection.mutable
import scala.io.Source

/**
 * @author John Sullivan
 */
object Relation {

  def main(args:Array[String]) {

    val doc = new Document(Source.fromFile(args(0)).getLines().mkString("\n")).setName(args(0).split("""/""").last)

    val relFinder = if(args.length >= 3) {
      new PatternBasedRelationFinder(PatternRelationPredictor.predictorsFromStreams(new FileInputStream(args(1)), new FileInputStream(args(2))))
    } else {
      ConllPatternBasedRelationFinder
    }

    val pipelineElements = Seq(
      OntonotesForwardPosTagger,
      NoEmbeddingsConllStackedChainNer,
      OntonotesTransitionBasedParser,
      ParseForwardCoref,
      relFinder 
    )
    val annoMap = DocumentAnnotatorPipeline.defaultDocumentAnnotationMap.toMap ++ Seq(classOf[RelationMentionList] -> (() => relFinder))
    val pipeline = DocumentAnnotatorPipeline(annoMap, Nil, pipelineElements.flatMap(_.postAttrs))
    println("loaded document")
    pipeline process doc
    println("processed pipeline")
    val relMentions = doc.attr[RelationMentionList]

    println("Detected Mentions: ")
    doc.coref.mentions.foreach { mention =>
      println(mention.phrase.string + " with type " + mention.phrase.head.nerTag.baseCategoryValue + " in sentence " + mention.phrase.sentence.string)
    }

    println("writing mentions")
    relMentions.foreach { rm =>
      rm.relations.value.foreach { relation =>
        if(rm.isArg1First) {
          println(rm.arg1.string + " " + relation.value + " " + rm.arg2.string + " %.4f ".format(relation.confidence) + relation.provenance)
        } else {
          println(rm.arg2.string + " " + relation.value + " " + rm.arg1.string + " %.4f ".format(relation.confidence) + relation.provenance)
        }
      }
    }
  }
}

object TACNerDomain extends CategoricalDomain[String] {
  this ++= "O ORG GPE_CITY GPE_COUNTRY GPE_STATE DATE PERSON CARDINAL AFFILIATION PERSON WEBSITE CAUSE_OF_DEATH LAW RELIGION TITLE".split(' ')
  freeze()
}

class TACNerTag(token:Token, initialCategory:String) extends NerTag(token, initialCategory) {
  def domain = TACNerDomain
}

object GoldRelation {

  def annotate(t:Token, annos:Seq[String]):Seq[MutableCategoricalVar[String]] = {
    annos.headOption.map(a => new TACNerTag(t, a)).toSeq
  }

  def main (args:Array[String]) {

    val relFinder = if(args.length >= 3) {
      new PatternBasedRelationFinder(PatternRelationPredictor.predictorsFromStreams(new FileInputStream(args(1)), new FileInputStream(args(2))))
    } else {
      ConllPatternBasedRelationFinder
    }

    val doc = LoadOWPL.fromFilename(args(0), annotate).head

    val coref = doc.getCoref

    var tokens = mutable.ArrayBuffer[Token]()
    val iter = doc.tokens.iterator

    while(iter.hasNext) {
      val t = iter.next()
      println("PRocessing: " + t.string)
      if(t.nerTag.baseCategoryValue != "O") {
        tokens.append(t)
      } else if (tokens.length > 0) {
        val ts = new TokenSpan(tokens)
        println("adding mention: " + ts.string)
        coref.addMention(new Phrase(ts))
        tokens.clear()
      }
    }
    if(tokens.length > 0) {
      val ts = new TokenSpan(tokens)
      println("adding mention: " + ts.string)
      coref.addMention(new Phrase(ts))
      tokens.clear()
    }

    relFinder.process(doc)
    val relMentions = doc.attr[RelationMentionList]

    println("Detected Mentions: ")
    doc.coref.mentions.foreach { mention =>
      println(mention.phrase.string + " with type " + mention.phrase.head.nerTag.baseCategoryValue + " in sentence " + mention.phrase.sentence.string)
    }

    println("writing mentions")
    relMentions.foreach { rm =>
      rm.relations.value.foreach { relation =>
        if(rm.isArg1First) {
          println(rm.arg1.string + " " + relation.value + " " + rm.arg2.string + " %.4f ".format(relation.confidence) + relation.provenance)
        } else {
          println(rm.arg2.string + " " + relation.value + " " + rm.arg1.string + " %.4f ".format(relation.confidence) + relation.provenance)
        }
      }
    }
  }
}